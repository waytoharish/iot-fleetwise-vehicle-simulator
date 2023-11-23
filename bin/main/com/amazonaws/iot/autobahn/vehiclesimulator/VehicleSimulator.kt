package com.amazonaws.iot.autobahn.vehiclesimulator

import com.amazonaws.iot.autobahn.config.ControlPlaneResources
import com.amazonaws.iot.autobahn.vehiclesimulator.cert.ACMCertificateManager
import com.amazonaws.iot.autobahn.vehiclesimulator.ecs.EcsTaskManager
import com.amazonaws.iot.autobahn.vehiclesimulator.edgeConfig.EdgeConfigProcessor
import com.amazonaws.iot.autobahn.vehiclesimulator.iot.IoTThingManager
import com.amazonaws.iot.autobahn.vehiclesimulator.iot.IoTThingManager.Companion.DEFAULT_POLICY_NAME
import com.amazonaws.iot.autobahn.vehiclesimulator.iot.IoTThingManager.Companion.DEFAULT_RICH_DATA_POLICY_DOCUMENT
import com.amazonaws.iot.autobahn.vehiclesimulator.iot.IoTThingManager.Companion.DEFAULT_RICH_DATA_ROLE_ALIAS
import com.amazonaws.iot.autobahn.vehiclesimulator.fw.FleetWiseManager
import com.amazonaws.iot.autobahn.vehiclesimulator.storage.S3Storage
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.acmpca.AcmPcaAsyncClient
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iot.IotAsyncClient
import software.amazon.awssdk.services.iotfleetwise.IoTFleetWiseAsyncClient
import software.amazon.awssdk.services.s3.S3AsyncClient
import java.time.Duration

/**
 * This is the main class of Vehicle Simulator. It integrates ECS, IoT Core and S3 modules to launch / stop FleetWise
 * Edge agent.
 * @param region is used to choose the physical location of the data center
 * @param arch CPU architecture for FleetWise Edge: arm64, amd64
 * @param s3Storage S3 bucket client
 * @param ioTThingManager module that handle IoT Thing lifecycle
 * @param acmCertificateManager module that provides functions to create and delete certificates against a private CA in AWS Certificate Manager
 * @param ecsTaskManager module that handle ECS Task lifecycle
 */
class VehicleSimulator(
    private val region: String,
    private val arch: String,
    private val s3Storage: S3Storage = S3Storage(S3AsyncClient.builder().region(Region.of(region)).build()),
    private val ioTThingManager: IoTThingManager = IoTThingManager(IotAsyncClient.builder().region(Region.of(region)).build(), s3Storage, IamClient.builder().region(Region.of(region)).build()),
    private val acmCertificateManager: ACMCertificateManager = ACMCertificateManager(AcmPcaAsyncClient.builder().region(Region.of(region)).build(), s3Storage),
    private val ecsTaskManager: EcsTaskManager = EcsTaskManager(EcsClient.builder().region(Region.of(region)).build(), arch),
    private val fwManager: FleetWiseManager = FleetWiseManager(IoTFleetWiseAsyncClient.builder().region(Region.of(region)).build())
) {
    private val log: Logger = LoggerFactory.getLogger(VehicleSimulator::class.java)
    /**
     * This function handles the setup tasks before launching vehicles.
     * It contains two subtasks
     * 1) Create IoT Things, Policy and Certificate and upload to user's S3 bucket
     * 2) Set MQTT connection section for Edge Static Config file based on FleetWise Test Stage and IoT Core data end point
     *    The config file will be uploaded to the user's S3 bucket
     * @param objectMapper The object mapper that parse json file
     * @param simulationMetaDataList list of simulation metadata contains vehicle IDs and corresponding S3 bucket/key
     * @param edgeConfigs a map of Edge static config file
     * @param stage FleetWise test stage. e.g: alpha, beta, gamma, prod
     * @param policyName the name for the to be created IoT Policy
     * @param policyDocument the policy document for the to be created IoT Policy
     * @param recreateIoTPolicyIfExists flag to indicate whether recreate or reuse if policy with the same name already exist
     * @param edgeUploadS3BucketName a bucket name  to create IAM role and IoT role alias to enable Edge access
     *     to that bucket. If empty no roles and role alias will be created. These are necessary for rich-data to upload
     *     Ion files from edge directly to S3
     * @return ThingOperationStatus contains two list of string, one list for successfully created IoT Things
     *          and one list for failed to create IoT Things
     */
    suspend fun preLaunch(
        objectMapper: ObjectMapper,
        simulationMetaDataList: List<SimulationMetaData>,
        edgeConfigs: Map<String, String>,
        policyName: String = DEFAULT_POLICY_NAME,
        policyDocument: String = DEFAULT_RICH_DATA_POLICY_DOCUMENT,
        recreateIoTPolicyIfExists: Boolean,
        edgeUploadS3BucketName: String = "",
        decoderManifest: String = "",
        vehicleModel: String = "",
        createVehicles: Boolean
    ): VehicleSetupStatus {
        // Get simulation input for provisioning
        val vehiclesToProvision = simulationMetaDataList.filter {
            it.provisionThing
        }

        val vehiclesWithPrivateCert = simulationMetaDataList.filter {
            !it.provisionThing
        }
        var thingCreationStatus = VehicleSetupStatus(emptySet(), emptySet())
        var privateCertCreationStatus = VehicleSetupStatus(emptySet(), emptySet())

        if (vehiclesToProvision.isNotEmpty()) {
            log.info("Creating IoT things for vehicles: ${vehiclesToProvision.size}")
            thingCreationStatus = ioTThingManager.createAndStoreThings(
                vehiclesToProvision,
                policyName = policyName,
                policyDocument = policyDocument,
                recreatePolicyIfAlreadyExists = recreateIoTPolicyIfExists,
                edgeUploadS3BucketName = edgeUploadS3BucketName,
            )
            if(createVehicles) {
                log.info("Creating FleetWise vehicles for:  ${vehiclesToProvision.size}")
                simulationMetaDataList.forEach {
                    fwManager.createVehicle(it.vehicleId, vehicleModel, decoderManifest)
                }
            }
        }
        
        if (vehiclesWithPrivateCert.isNotEmpty()) {
            log.info("Creating and uploading private keys and certs for vehicles: ${vehiclesWithPrivateCert.size}")
            privateCertCreationStatus = acmCertificateManager.setupVehiclesWithCertAndPrivateKey(vehiclesWithPrivateCert)
        }
        val vehicleSetupStatus = VehicleSetupStatus(
            thingCreationStatus.successList + privateCertCreationStatus.successList,
            thingCreationStatus.failedList + privateCertCreationStatus.failedList
        )
        val config = EdgeConfigProcessor(ControlPlaneResources(region), objectMapper)
        // Compose the new config with MQTT parameters based on FleetWise Test Stage and IoT Core data end point
        log.info("Creating Edge static configs")
        val configMapWithCredentialsProvider = config.setCredentialsProviderParameter(edgeConfigs, DEFAULT_RICH_DATA_ROLE_ALIAS, ioTThingManager.getIoTCoreDataEndPoint("iot:CredentialProvider"))
        val newConfigMap = config.setMqttConnectionParameter(configMapWithCredentialsProvider, ioTThingManager.getIoTCoreDataEndPoint())
        log.info("Uploading Edge static configs to S3")
        simulationMetaDataList.forEach {
            newConfigMap[it.vehicleId]?.let {
                config ->
                s3Storage.put(it.s3.bucket, "${it.s3.key}/$CONFIG_FILE_NAME", config.toByteArray())
            }
        }
        return vehicleSetupStatus
    }

    /**
     * This function launches the vehicles by starting new ECS tasks. If it's the first time launching vehicles, preLaunch
     * needs to be called to set up the required files and upload to user's S3 bucket.
     * @param simulationMetaDataList list of simulation metadata contains vehicle IDs and corresponding S3 bucket/key
     * @param ecsTaskDefinition ECS Task Definition Name. The task definition needs to be created ahead of time.
     * This function doesn't create task definition
     * @param ecsCapacityProviderName ECS Capacity Provider is used to scale in/out of ECS instances to host tasks.
     * This function doesn't create capacity provider. It needs to be created ahead of time
     * @param tags Key Value Pairs to be tagged on each tasks for identification purpose
     * @param timeout specify maximum wait time before giving up waiting for all ECS tasks to be running.
     * @param retries specify maximum retries before giving up waiting for all ECS tasks to be running.
     * @return list of launchStatus containing running Vehicle ID and corresponding ECS Task Arn
     */
    fun launchVehicles(
        simulationMetaDataList: List<SimulationMetaData>,
        ecsTaskDefinition: String = "fwe-$arch-with-cw",
        ecsCapacityProviderName: String = "ubuntu-$arch-capacity-provider",
        tags: Map<String, String> = mapOf(),
        timeout: Duration = Duration.ofMinutes(5),
        retries: Int = 100
    ): List<LaunchStatus> {
        log.info("running ECS Tasks")
        val ecsLaunchStatus = ecsTaskManager.runTasks(
            simulationMetaDataList,
            ecsTaskDefinition = ecsTaskDefinition,
            useCapacityProvider = true,
            ecsCapacityProviderName = ecsCapacityProviderName,
            tags = tags,
            waiterTimeout = timeout,
            waiterRetries = retries
        )
        return ecsLaunchStatus.map { LaunchStatus(it.key, it.value) }
    }

    /**
     * This function stop the vehicles by stopping ECS tasks. It doesn't delete the IoT Thing or delete the file
     * from user's S3 simulation bucket.
     * @param taskIDList list of ECS Tasks to be stopped
     * @param timeout specify maximum wait time before giving up waiting for all ECS tasks to be stopped.
     * @param retries specify maximum retries before giving up waiting for all ECS tasks to be stopped.
     * @return StopStatus contains a List of stopped task IDs and a list of failed to stop task IDs
     */
    fun stopVehicles(
        taskIDList: List<String>,
        timeout: Duration = Duration.ofMinutes(5),
        retries: Int = 100
    ): StopStatus {
        log.info("stopping ECS Tasks")
        val stoppedTaskIDList = ecsTaskManager.stopTasks(
            taskIDList,
            timeout,
            retries
        )
        return StopStatus(stoppedTaskIDList.toSet(), taskIDList.toSet() - stoppedTaskIDList.toSet())
    }

    /**
     * This function stop the vehicles by stopping ECS tasks. It doesn't delete the IoT Thing or delete the file
     * from user's S3 simulation bucket.
     * @param simulationMetaDataList list of simulation metadata contains vehicle IDs and corresponding S3 bucket/key
     * @param policyName The name of IoT Policy to be deleted
     * @param deleteIoTPolicy flag to specify whether to delete IoT Policy
     * @param deleteIoTCert flag to specify whether to delete IoT Certificate
     * @return ThingOperationStatus contains two list of string, one list for successfully deleted IoT Things
     *          and one list for failed to delete IoT Things
     */
    suspend fun clean(
        simulationMetaDataList: List<SimulationMetaData>,
        policyName: String = DEFAULT_POLICY_NAME,
        deleteIoTPolicy: Boolean = false,
        deleteIoTCert: Boolean = false
    ): VehicleSetupStatus {
        log.info("Deleting IoT things")
        val thingDeletionStatus = ioTThingManager.deleteThings(
            simulationMetaDataList,
            policyName = policyName,
            deletePolicy = deleteIoTPolicy,
            deleteCert = deleteIoTCert
        )
        log.info("Deleting simulation files from S3")
        // For S3 deletion, we first group keys by bucket so that items from the same bucket can be deleted together
        simulationMetaDataList.groupBy { it.s3.bucket }.mapValues { it -> it.value.map { it.s3.key } }.map {
            (bucket, keys) ->
            s3Storage.deleteObjects(
                bucket,
                keys.flatMap { key ->
                    s3Storage.listObjects(bucket, key)
                }
            )
        }
        return thingDeletionStatus
    }

    companion object {
        const val CONFIG_FILE_NAME = "config.json"

        data class LaunchStatus(
            val vehicleID: String,
            val taskArn: String
        )

        data class StopStatus(
            val successList: Set<String>,
            val failedList: Set<String>
        )
    }
}
