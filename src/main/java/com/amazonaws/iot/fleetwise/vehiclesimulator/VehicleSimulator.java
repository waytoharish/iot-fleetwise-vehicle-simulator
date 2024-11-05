package com.amazonaws.iot.fleetwise.vehiclesimulator;

import com.amazonaws.iot.fleetwise.vehiclesimulator.config.ControlPlaneResources;
import com.amazonaws.iot.fleetwise.vehiclesimulator.cert.ACMCertificateManager;
import com.amazonaws.iot.fleetwise.vehiclesimulator.config.StageAndRegion;
import com.amazonaws.iot.fleetwise.vehiclesimulator.ecs.EcsTaskManager;
import com.amazonaws.iot.fleetwise.vehiclesimulator.edgeConfig.EdgeConfigProcessor;
import com.amazonaws.iot.fleetwise.vehiclesimulator.exceptions.EcsTaskManagerException;
import com.amazonaws.iot.fleetwise.vehiclesimulator.fw.FleetWiseManager;
import com.amazonaws.iot.fleetwise.vehiclesimulator.iot.IoTThingManager;
import com.amazonaws.iot.fleetwise.vehiclesimulator.storage.S3Storage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.acmpca.AcmPcaAsyncClient;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iot.IotAsyncClient;
import software.amazon.awssdk.services.iotfleetwise.IoTFleetWiseAsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import static java.util.Collections.emptySet;


public final class VehicleSimulator {

    private final String region;

    private final String arch;

    private final S3Storage s3Storage;

    private final IoTThingManager ioTThingManager;

    private final ACMCertificateManager acmCertificateManager;

    private final EcsTaskManager ecsTaskManager;

    private final FleetWiseManager fwManager;

    private final Logger log = LoggerFactory.getLogger(VehicleSimulator.class);

    public static final String CONFIG_FILE_NAME = "config.json";
    public static final String DEFAULT_RICH_DATA_ROLE_ALIAS = "vehicle-simulator-credentials-provider";
    public static final String DEFAULT_RICH_DATA_POLICY_DOCUMENT = "{\n\"Version\": \"2012-10-17\",\n\"Statement\": [\n{\n\"Effect\": \"Allow\",\n\"Action\":[\n\"iot:Connect\",\n\"iot:Subscribe\",\n\"iot:Publish\",\n\"iot:Receive\",\n\"iot:AssumeRoleWithCertificate\"\n],\n\"Resource\": [\n\"*\"\n]\n}\n]\n}";
    public static String DEFAULT_POLICY_NAME = "vehicle-simulator-policy";

    public VehicleSimulator(String region,  String arch,  S3Storage s3Storage,  IoTThingManager ioTThingManager,  ACMCertificateManager acmCertificateManager,  EcsTaskManager ecsTaskManager,  FleetWiseManager fwManager) {
        this.region = region;
        this.arch = arch;
        this.s3Storage = Objects.requireNonNullElseGet(s3Storage, () -> new S3Storage(S3AsyncClient.builder().region(Region.of(region)).build()));
        this.ioTThingManager = Objects.requireNonNullElseGet(ioTThingManager, () -> new IoTThingManager(IotAsyncClient.builder().region(Region.of(region)).build(), s3Storage, IamClient.builder().region(Region.of(region)).build()));
        this.acmCertificateManager = Objects.requireNonNullElseGet(acmCertificateManager, () -> new ACMCertificateManager(AcmPcaAsyncClient.builder().region(Region.of(region)).build(), s3Storage));
        this.ecsTaskManager = Objects.requireNonNullElseGet(ecsTaskManager, () -> new EcsTaskManager(EcsClient.builder().region(Region.of(region)).build(), arch, "vehicle-simulator-" + arch));
        this.fwManager = Objects.requireNonNullElseGet(fwManager, () -> new FleetWiseManager(IoTFleetWiseAsyncClient.builder().region(Region.of(region)).build()));
    }

    @Nullable
    public VehicleSetupStatus preLaunch(ObjectMapper objectMapper,
                                        List<SimulationMetaData> simulationMetaDataList,
                                        Map<String,String> edgeConfigs, String policyName,
                                        String policyDocument,
                                        boolean recreateIoTPolicyIfExists,
                                        String edgeUploadS3BucketName,
                                        String decoderManifest,
                                        String vehicleModel,
                                        boolean createVehicles) {
        // Get simulation input for provisioning
        List<SimulationMetaData> vehiclesToProvision = simulationMetaDataList.stream().filter(SimulationMetaData::isProvisionThing).collect(Collectors.toList());
        List<SimulationMetaData> vehiclesWithPrivateCert = simulationMetaDataList.stream().filter(i -> !i.isProvisionThing()).collect(Collectors.toList());

        VehicleSetupStatus thingCreationStatus = new VehicleSetupStatus(emptySet(), emptySet());
        VehicleSetupStatus privateCertCreationStatus = new VehicleSetupStatus(emptySet(), emptySet());

        if (!vehiclesToProvision.isEmpty()) {
            log.info("Creating IoT things for vehicles: {}", vehiclesToProvision.size());
            if(policyName == null || policyName.isEmpty()){
                policyName = DEFAULT_POLICY_NAME;
            }
            if(policyDocument == null || policyDocument.isEmpty()){
                policyDocument = DEFAULT_RICH_DATA_POLICY_DOCUMENT;
            }
            if(edgeUploadS3BucketName == null){
                edgeUploadS3BucketName="";
            }
            thingCreationStatus = ioTThingManager.createAndStoreThings(
                    vehiclesToProvision,
                    policyName,
                    policyDocument,
                    recreateIoTPolicyIfExists,
                    edgeUploadS3BucketName
                    );
            if(createVehicles) {
                log.info("Creating FleetWise vehicles for:  {}", vehiclesToProvision.size());
                simulationMetaDataList.forEach (it ->
                    fwManager.createVehicle(it.getVehicleId(), vehicleModel, decoderManifest));
            }
        }

        if (!vehiclesWithPrivateCert.isEmpty()) {
            log.info("Creating and uploading private keys and certs for vehicles: {}", vehiclesWithPrivateCert.size());
            privateCertCreationStatus = acmCertificateManager.setupVehiclesWithCertAndPrivateKey(vehiclesWithPrivateCert);
        }
        VehicleSetupStatus vehicleSetupStatus = new VehicleSetupStatus(
                Stream.concat(thingCreationStatus.successList().stream(), privateCertCreationStatus.successList().stream())
                        .collect(Collectors.toSet()),
                Stream.concat(thingCreationStatus.failedList().stream(), privateCertCreationStatus.failedList().stream())
                        .collect(Collectors.toSet()));

        ControlPlaneResources controlPlaneResources =new ControlPlaneResources(region,"prod", new StageAndRegion("prod", region), "", "AWSServiceRoleForIoTFleetWise", "prod"+region);
        EdgeConfigProcessor config = new EdgeConfigProcessor(controlPlaneResources, objectMapper);

        // Compose the new config with MQTT parameters based on FleetWise Test Stage and IoT Core data end point
        log.info("Creating Edge static configs");
        Map<String, String> configMapWithCredentialsProvider = config.setCredentialsProviderParameter(edgeConfigs, DEFAULT_RICH_DATA_ROLE_ALIAS, ioTThingManager.getIoTCoreDataEndPoint("iot:CredentialProvider"));
        Map<String, String>  newConfigMap = config.setMqttConnectionParameter(configMapWithCredentialsProvider, ioTThingManager.getIoTCoreDataEndPoint("iot:Data-ATS"));
        log.info("Uploading Edge static configs to S3");
        simulationMetaDataList.forEach ( it ->
                {
                    String cfg = newConfigMap.get(it.getVehicleId());
                    String key = it.getS3().getKey() + "/"+ CONFIG_FILE_NAME;
                    s3Storage.put(it.getS3().getBucket(), key, cfg.getBytes(StandardCharsets.UTF_8));
                }
        );
        return vehicleSetupStatus;
    }


    public List<LaunchStatus>  launchVehicles( List<SimulationMetaData> simulationMetaDataList,
                                               String ecsTaskDefinition,  String ecsCapacityProviderName,
                                               Map<String, String> tags,
                                               Duration timeout,
                                               int retries) throws EcsTaskManagerException {
        this.log.info("running ECS Tasks");
        Map<String, String> ecsLaunchStatus = ecsTaskManager.runTasks(simulationMetaDataList,
                                            ecsTaskDefinition, true,
                                "EC2",
                                             ecsCapacityProviderName,
                                            tags,
                                            timeout,
                                            retries);
        return ecsLaunchStatus.entrySet().stream().map(it-> new LaunchStatus((String)it.getKey(), (String)it.getValue())).collect(Collectors.toList());
    }


    public final StopStatus stopVehicles( List<String> taskIDList,  Duration timeout, int retries) {
        this.log.info("stopping ECS Tasks");
        List<String>  stoppedTaskIDList = this.ecsTaskManager.stopTasks(taskIDList, timeout, retries);
        taskIDList.removeAll(stoppedTaskIDList);
        return new StopStatus(new HashSet<>(stoppedTaskIDList), new HashSet<>(taskIDList));
    }

    @Nullable
    public final VehicleSetupStatus clean( List<SimulationMetaData> simulationMetaDataList,  String policyName, boolean deleteIoTPolicy, boolean deleteIoTCert) {
        if(policyName == null || policyName.isEmpty()){
            policyName = DEFAULT_POLICY_NAME;
        }
        this.log.info("Deleting IoT things");
        VehicleSetupStatus thingDeletionStatus = ioTThingManager.deleteThings(
                simulationMetaDataList,
                policyName,
                deleteIoTPolicy,
                deleteIoTCert
        );
        this.log.info("Deleting simulation files from S3");
        // For S3 deletion, we first group keys by bucket so that items from the same bucket can be deleted together
        simulationMetaDataList.stream()
                .collect(Collectors.groupingBy(
                                it -> it.getS3().getBucket(),
                                Collectors.mapping(it -> it.getS3().getKey(), Collectors.toList())
                        )
                ).forEach(s3Storage::deleteObjects);

        return thingDeletionStatus;

}
        public static final class LaunchStatus {

            private final String vehicleID;

            private final String taskArn;

            public LaunchStatus( String vehicleID,  String taskArn) {
                this.vehicleID = vehicleID;
                this.taskArn = taskArn;
            }


            public final String getVehicleID() {
                return this.vehicleID;
            }


            public final String getTaskArn() {
                return this.taskArn;
            }


            public final String component1() {
                return this.vehicleID;
            }


            public final String component2() {
                return this.taskArn;
            }


            public final LaunchStatus copy( String vehicleID,  String taskArn) {
                Intrinsics.checkNotNullParameter(vehicleID, "vehicleID");
                Intrinsics.checkNotNullParameter(taskArn, "taskArn");
                return new LaunchStatus(vehicleID, taskArn);
            }


            public String toString() {
                return "LaunchStatus(vehicleID=" + this.vehicleID + ", taskArn=" + this.taskArn + ')';
            }

            public int hashCode() {
                int result = this.vehicleID.hashCode();
                result = result * 31 + this.taskArn.hashCode();
                return result;
            }

            public boolean equals(@Nullable Object other) {
                if (this == other) {
                    return true;
                } else if (!(other instanceof LaunchStatus)) {
                    return false;
                } else {
                    LaunchStatus var2 = (LaunchStatus)other;
                    if (!Intrinsics.areEqual(this.vehicleID, var2.vehicleID)) {
                        return false;
                    } else {
                        return Intrinsics.areEqual(this.taskArn, var2.taskArn);
                    }
                }
            }
        }

        public static final class StopStatus {

            private final Set<String> successList;

            private final Set<String> failedList;

            public StopStatus( Set<String> successList,  Set<String> failedList) {
                this.successList = successList;
                this.failedList = failedList;
            }


            public final Set<String> getSuccessList() {
                return this.successList;
            }


            public final Set<String> getFailedList() {
                return this.failedList;
            }


            public final Set<String> component1() {
                return this.successList;
            }


            public final Set<String> component2() {
                return this.failedList;
            }


            public String toString() {
                return "StopStatus(successList=" + this.successList + ", failedList=" + this.failedList + ')';
            }

            public int hashCode() {
                int result = this.successList.hashCode();
                result = result * 31 + this.failedList.hashCode();
                return result;
            }

            public boolean equals(@Nullable Object other) {
                if (this == other) {
                    return true;
                } else if (!(other instanceof StopStatus)) {
                    return false;
                } else {
                    StopStatus var2 = (StopStatus)other;
                    if (!Intrinsics.areEqual(this.successList, var2.successList)) {
                        return false;
                    } else {
                        return Intrinsics.areEqual(this.failedList, var2.failedList);
                    }
                }
            }
        }
    }
