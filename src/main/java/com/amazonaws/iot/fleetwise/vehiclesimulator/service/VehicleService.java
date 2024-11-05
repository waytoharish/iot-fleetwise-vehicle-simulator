package com.amazonaws.iot.fleetwise.vehiclesimulator.service;

import com.amazonaws.iot.fleetwise.vehiclesimulator.SimulationMetaData;
import com.amazonaws.iot.fleetwise.vehiclesimulator.bean.SimulatorCliInput;
import com.amazonaws.iot.fleetwise.vehiclesimulator.VehicleSetupStatus;
import com.amazonaws.iot.fleetwise.vehiclesimulator.VehicleSimulator;
import com.amazonaws.iot.fleetwise.vehiclesimulator.bean.StopVehicles;
import com.amazonaws.iot.fleetwise.vehiclesimulator.cli.LaunchVehicles;
import com.amazonaws.iot.fleetwise.vehiclesimulator.exceptions.EcsTaskManagerException;
import com.amazonaws.iot.fleetwise.vehiclesimulator.storage.S3;
import com.amazonaws.iot.fleetwise.vehiclesimulator.storage.S3Storage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class VehicleService {
    private final Logger log = LoggerFactory.getLogger(LaunchVehicles.class);

    @Autowired
    ResourceLoader resourceLoader;

    public Integer launchVehicle(com.amazonaws.iot.fleetwise.vehiclesimulator.bean.LaunchVehicle launchVehicle) throws EcsTaskManagerException {

        this.log.info("Simulation Map file {} and region {} and platform {}", launchVehicle.getSimulationInput(), launchVehicle.getRegion(),launchVehicle.getCpuArchitecture());


        final List<SimulatorCliInput> simConfig = parseSimulationFile(launchVehicle.getSimulationInput());

        int vehicleToProvision  = launchVehicle.getVehicleToProvision();


        List<SimulatorCliInput> simConfigMaps = new ArrayList<>();

        for (int i = 0; i < vehicleToProvision; i++) {
            SimulationMetaData simulationMetaData = SimulationMetaData.builder().build();
            var vehicleId = launchVehicle.getVehicleNamePrefix()+i;
            S3 s3 = S3.builder().build();

            s3.setBucket(launchVehicle.getS3Upload().getBucket());
            s3.setKey(launchVehicle.getS3Upload().getKey()+"/"+vehicleId);

            simulationMetaData.setVehicleId(vehicleId);
            simulationMetaData.setDeviceCertificateConfig(simConfig.get(0).getSimulationMetaData().getDeviceCertificateConfig());
            simulationMetaData.setS3(s3);
            simulationMetaData.setProvisionThing(simConfig.get(0).getSimulationMetaData().isProvisionThing());

            SimulatorCliInput simulatorCliInput = SimulatorCliInput.builder().build();
            simulatorCliInput.setConfigPath(simConfig.get(0).getConfigPath());
            simulatorCliInput.setLocalPath(simConfig.get(0).getLocalPath());
            simulatorCliInput.setSimulationMetaData(simulationMetaData);

            simConfigMaps.add(simulatorCliInput);
        }

        this.log.info("Size of the list {} ", simConfigMaps.size());
        
        final Map<String, String> edgeConfigFiles = this.readConfigFile(simConfigMaps);

        S3Storage s3Storage = new S3Storage(S3AsyncClient.builder().region(Region.of(launchVehicle.getRegion())).build());

        this.log.info("Uploading simulation files to S3");
        uploadLocalSimFiles(simConfigMaps, s3Storage);

        VehicleSimulator vehicleSimulator = new VehicleSimulator(launchVehicle.getRegion(),
                launchVehicle.getCpuArchitecture(),
                s3Storage, null,
                null, null, null);

        VehicleSetupStatus thingCreationStatus =
                vehicleSimulator.preLaunch(
                        new ObjectMapper(),
                        simConfigMaps.stream().map( SimulatorCliInput::getSimulationMetaData ).collect(Collectors.toList()),
                        edgeConfigFiles,null,null,
                        launchVehicle.isRecreateIoTPolicyIfExists(),
                        null,
                        launchVehicle.getDecoderManifestArn(),
                        launchVehicle.getVehicleModelArn(),
                       launchVehicle.isCreateVehicles()
                );

        log.info("Set up vehicles: {}", thingCreationStatus.successList());

        if (!thingCreationStatus.failedList().isEmpty()) {
            log.error("Failed to setup vehicles: {}", thingCreationStatus.failedList());
            log.error("Cannot continue simulation as not all things are created successful");
            return -1;
        }
        List<VehicleSimulator.LaunchStatus>  launchStatus = vehicleSimulator.launchVehicles(
                simConfigMaps.stream().map(SimulatorCliInput::getSimulationMetaData).collect(Collectors.toList()),
                launchVehicle.getEcsTaskDefinition(),
                "ubuntu-"+ launchVehicle.getCpuArchitecture() +"-capacity-provider",
                launchVehicle.getTags().stream().collect(Collectors.toMap(Function.identity(), item -> item)),
                Duration.ofMinutes(launchVehicle.getEcsWaiterTimeout()),
                launchVehicle.getEcsWaiterRetries()
        );

        log.info("Launched vehicles: {}", launchStatus);
        log.info("Finished launching");


        List<String> list1 = simConfigMaps.stream().map(it -> it.getSimulationMetaData().getVehicleId()).toList();
        List<String> list2 = launchStatus.stream().map(VehicleSimulator.LaunchStatus::getVehicleID).toList();
        if (list1.equals(list2)) {
            return 0;
        } else {
            return -1;
        }
    }


    public Integer stopVehicle(StopVehicles stopVehicles) {
        int result = 0;
        S3AsyncClient s3AsyncClient = S3AsyncClient.builder().region(Region.of(stopVehicles.getRegion())).build();
        S3Storage s3Storage = new S3Storage(s3AsyncClient);
        final VehicleSimulator vehicleSimulator = new VehicleSimulator(stopVehicles.getRegion(), stopVehicles.getCpuArchitecture(), s3Storage,null, null,  null, null);
        VehicleSimulator.StopStatus stopStatus = vehicleSimulator.stopVehicles(stopVehicles.getEcsTaskIDs(), Duration.ofMinutes((long) stopVehicles.getEcsWaiterTimeout()), stopVehicles.getEcsWaiterRetries());
        this.log.info("Successfully stopped ecs tasks: {}", stopStatus.getSuccessList());
        if (!stopStatus.getFailedList().isEmpty()) {
            this.log.error("Failed to stop ecs tasks: {}", stopStatus.getFailedList());
            result = -1;
        }

        if (stopVehicles.getSimulationInput() != null && !stopVehicles.getSimulationInput().isEmpty()) {
            List<SimulationMetaData> simulationMetaData = parseSimulationFile(stopVehicles.getSimulationInput())
                    .stream().map(SimulatorCliInput::getSimulationMetaData).collect(Collectors.toList());

            VehicleSetupStatus thingDeletionStatus = vehicleSimulator.clean(
                    simulationMetaData,
                    null,
                    stopVehicles.isDeleteIoTPolicy(),
                    stopVehicles.isDeleteIoTCert()

            );
            log.info("Successfully deleted things: {}", thingDeletionStatus);
            if (thingDeletionStatus != null && thingDeletionStatus.failedList() !=null && !thingDeletionStatus.failedList().isEmpty()) {
                log.error("Failed to delete things: {}", thingDeletionStatus.failedList());
                result = -1;
            }
            log.info("Finish clean up");
        }
        return result;
    }

    private List<SimulatorCliInput> parseSimulationFile(String simulationPackage) {
        try {
            Resource resource=resourceLoader.getResource("classpath:"+simulationPackage);
            return new ObjectMapper().readValue( resource.getFile(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void  uploadLocalSimFiles( List<SimulatorCliInput> simConfigMaps, S3Storage s3Storage) {
        simConfigMaps.stream().filter(it -> !it.getLocalPath().isEmpty())
                .forEach(
                        simConfig -> {
                            try {
                                List<Path> list = Files.walk(Paths.get(simConfig.getLocalPath()))
                                        .filter(Files::isRegularFile).toList();
                                list.forEach(it -> {
                                            File file = new File(it.toAbsolutePath().toString());
                                            byte[] fileStream = null;
                                            try {
                                                fileStream = Files.readAllBytes(Paths.get(file.getPath()));
                                                s3Storage.put(simConfig.getSimulationMetaData().getS3().getBucket(), simConfig.getSimulationMetaData().getS3().getKey() +"/sim/" + Paths.get(simConfig.getLocalPath()).relativize(it), fileStream);
                                            } catch (Exception e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                );
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                );
    }

    private Map<String, String> readConfigFile(List<SimulatorCliInput> simConfigMaps) {
        return simConfigMaps.stream().collect(Collectors.toMap(item -> item.getSimulationMetaData().getVehicleId(),item -> {
            try {
                return new String(Files.readAllBytes(Paths.get(item.getConfigPath())));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }

}
