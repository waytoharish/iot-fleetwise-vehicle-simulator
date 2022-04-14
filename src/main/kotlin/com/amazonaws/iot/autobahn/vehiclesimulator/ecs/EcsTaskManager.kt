package com.amazonaws.iot.autobahn.vehiclesimulator.ecs

import com.amazonaws.iot.autobahn.vehiclesimulator.SimulationMetaData
import com.amazonaws.iot.autobahn.vehiclesimulator.exceptions.EcsTaskManagerException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.AccessDeniedException
import software.amazon.awssdk.services.ecs.model.CapacityProviderStrategyItem
import software.amazon.awssdk.services.ecs.model.ClusterNotFoundException
import software.amazon.awssdk.services.ecs.model.ContainerOverride
import software.amazon.awssdk.services.ecs.model.InvalidParameterException
import software.amazon.awssdk.services.ecs.model.KeyValuePair
import software.amazon.awssdk.services.ecs.model.RunTaskRequest
import software.amazon.awssdk.services.ecs.model.Tag
import software.amazon.awssdk.services.ecs.model.TaskOverride
import java.time.Duration

/**
 * ECS Task Manager is responsible for starting / stopping ECS tasks to simulate FleetWise Edge agents
 * @param ecsClient aws ecs client to access ecs functions
 * @param arch CPU architecture for FleetWise Edge agent
 * @param ecsClusterName cluster name
 */
class EcsTaskManager(
    private val ecsClient: EcsClient,
    private val arch: String,
    private val ecsClusterName: String = "vehicle-simulator-$arch"
) {
    companion object {
        // Per ECS service quota, only 10 tasks can be requested through runTask API. Hence, if we request to start
        // more tasks than 10, we need to loop through
        const val MAX_TASK_PER_RUN_TASK_REQUEST = 10
        // Per ECS service quota, only 100 tasks can be requested through waiter API
        const val MAX_TASK_PER_WAIT_TASK = 100
    }
    private val log: Logger = LoggerFactory.getLogger(EcsTaskManager::class.java)

    /**
     * This function interacts with ECS to run tasks hosted on EC2 machines.
     * The Key Value Pair is passed through container environment variable.
     * The function won't return until task last status is running or timeout or exception
     *
     * Note this function assumes cluster and task definition have been set up previously
     *
     * @param vehicleSimulationMap: A map containing Vehicle ID to Simulation URL.
     *                              e.g: car1: S3://simulation/car1
     * @param ecsTaskDefinition: Name of task definition
     * @param useCapacityProvider: Boolean flag for if using capacity provider. If not LaunchType will be used
     * @param ecsLaunchType: Launch Type when not using capacity provider
     * @param ecsCapacityProviderName: Name for capacity provider
     * @param waiterTimeout: maximum timeout for ECS waiter to wait for task to run
     * @param waiterRetries: maximum retries for ECS waiter to wait for task to run
     * @return a list of running task
     * @throws EcsTaskManagerException if one of the following exception occurs:
     * 1) Cluster Not Found
     * 2) Invalid request
     * 3) Access Denied
     * 4) SdkClientException raised from ECS waiter, usually caused by timeout
     * 5) UnsupportedOperationException raised from ECS waiter
     */
    fun runTasks(
        vehicleSimulationMap: List<SimulationMetaData>,
        ecsTaskDefinition: String = "fwe-$arch-with-cw",
        useCapacityProvider: Boolean = true,
        ecsLaunchType: String = "EC2",
        ecsCapacityProviderName: String = "ubuntu-$arch-capacity-provider",
        tags: Map<String, String> = mapOf(),
        waiterTimeout: Duration = Duration.ofMinutes(5),
        waiterRetries: Int = 100
    ): Map<String, String> {
        log.info("Run Tasks on ECS Cluster: $ecsClusterName with task definition: $ecsTaskDefinition")
        if (useCapacityProvider) {
            log.info("Use Capacity Provider: $ecsCapacityProviderName")
        }
        // Here we create Task per Vehicle.
        val pendingTaskArnList = vehicleSimulationMap.mapNotNull { vehicleIDToSimUrl ->
            // We pass the vehicle ID and simulation URL through environment variable.
            val envList = listOf<KeyValuePair>(
                KeyValuePair.builder().name("VEHICLE_ID").value(vehicleIDToSimUrl.vehicleId).build(),
                KeyValuePair.builder().name("S3_BUCKET").value(vehicleIDToSimUrl.s3.bucket).build(),
                KeyValuePair.builder().name("S3_KEY").value(vehicleIDToSimUrl.s3.key).build()
            ) + tags.map { KeyValuePair.builder().name(it.key).value(it.value).build() }
            val containerOverride = ContainerOverride.builder()
                .environment(envList)
                .name("fwe-$arch")
                .build()
            // Build the task override
            val taskOverride = TaskOverride.builder()
                .containerOverrides(listOf(containerOverride))
                .build()
            // Check whether we want to use Capacity Provider. If not, use "EC2" as launch type
            val taskBuilder = if (useCapacityProvider)
                RunTaskRequest.builder()
                    .capacityProviderStrategy(
                        CapacityProviderStrategyItem.builder()
                            .capacityProvider(ecsCapacityProviderName)
                            .build()
                    )
            else
                RunTaskRequest.builder()
                    .launchType(ecsLaunchType)
            // Build the runTaskRequest
            val runTaskRequest = taskBuilder
                .cluster(ecsClusterName)
                .count(1)
                .taskDefinition(ecsTaskDefinition)
                .overrides(taskOverride)
                .tags(
                    (
                        tags + mapOf(
                            "vehicleID" to vehicleIDToSimUrl.vehicleId,
                            "s3Bucket" to vehicleIDToSimUrl.s3.bucket,
                            "s3Key" to vehicleIDToSimUrl.s3.key
                        )
                        ).map { Tag.builder().key(it.key).value(it.value).build() }
                )
                .build()
            val taskArnList = try {
                ecsClient.runTask(runTaskRequest).tasks().map { it.taskArn() }
            } catch (ex: ClusterNotFoundException) {
                throw EcsTaskManagerException("Cluster Not Found Exception", ex)
            } catch (ex: InvalidParameterException) {
                throw EcsTaskManagerException("Invalid Parameter Exception", ex)
            } catch (ex: AccessDeniedException) {
                throw EcsTaskManagerException("Access Denied Exception", ex)
            }
            if (taskArnList.size == 1) {
                Pair(vehicleIDToSimUrl.vehicleId, taskArnList[0])
            } else {
                log.error("vehicle ${vehicleIDToSimUrl.vehicleId} RunTaskResponse contains ${taskArnList.size} tasks: $taskArnList")
                null
            }
        }.toMap()
        log.info("Starting Task: $pendingTaskArnList")
        log.info("Wait for all tasks running")
        if (pendingTaskArnList.size != vehicleSimulationMap.size) {
            throw EcsTaskManagerException("Failed to create all tasks", pendingTaskArnList)
        }
        // Maximum number of tasks per ecsClient waiter is 100.
        // Hence, we need to chunk the large task lists into smaller lists before requesting wait
        // Note although the waiters runs one by one, the loop latency should be small
        // as all the tasks have already been created by runTask in previous step.
        // We only return the Task Arns that are successfully running
        return pendingTaskArnList.entries
            .chunked(MAX_TASK_PER_WAIT_TASK)
            .flatMap { chunkedMap ->
                val waiterResponse = try {
                    ecsClient.waiter().waitUntilTasksRunning(
                        { builder ->
                            builder.cluster(ecsClusterName)
                                .tasks(chunkedMap.map { it.value })
                                .build()
                        },
                        { builder -> builder.waitTimeout(waiterTimeout).maxAttempts(waiterRetries).build() }
                    )
                } catch (ex: UnsupportedOperationException) {
                    throw EcsTaskManagerException("UnsupportedOperationException raised while waiting for all tasks running", pendingTaskArnList)
                } catch (ex: SdkClientException) {
                    throw EcsTaskManagerException("SdkClientException raised while waiting for all tasks running", pendingTaskArnList)
                }
                val taskArnToVehicleIDMap = chunkedMap.associate { (k, v) -> v to k }
                // We only return the Task Arns that are successfully running
                waiterResponse.matched().response().get().tasks().filter {
                    it.lastStatus() == "RUNNING"
                }.map { taskArnToVehicleIDMap[it.taskArn()]!! to it.taskArn() }
            }.associate { it.first to it.second }
    }

    /**
     * This function takes task ID as input. It will interact with ECS to stop the task.
     * The function won't return until task last status is stopped or timeout or thrown exception
     * Note this function assume cluster has been set up previously
     *
     * @param taskIDList: a list of Task ID that client wants to stop
     * @param waiterTimeout: maximum timeout for ECS waiter to wait for task to stop
     * @param waiterRetries: maximum retries for ECS waiter to wait for task to stop
     * @return list of stopped tasks
     * @throws EcsTaskManagerException: If the one of the following scenario occurs:
     * 1) Ecs Cluster Not Found
     * 2) Task failed to stop
     * 3) SdkClientException raised from ECS waiter, usually caused by timeout
     * 4) UnsupportedOperationException raised from ECS waiter
     */
    fun stopTasks(
        taskIDList: List<String>,
        waiterTimeout: Duration = Duration.ofMinutes(5),
        waiterRetries: Int = 100
    ): List<String> {
        // First get a list of stopping task
        val stoppingTaskIDs = taskIDList.mapNotNull {
            try {
                ecsClient.stopTask { builder ->
                    builder.cluster(ecsClusterName)
                        .task(it)
                        .reason("stop the task")
                }
                // by the time we got here, the stopTask request has been accepted without exception
                // add the taskID to stoppingTaskIDs list
                it
            } catch (ex: ClusterNotFoundException) {
                throw EcsTaskManagerException("Cluster Not Found Exception for stopping taskID $it", ex)
            } catch (ex: InvalidParameterException) {
                // This exception will be raised if one taskID couldn't be found. We should not wait for it
                // to be stopped in the waiting stage next
                log.warn("Invalid Parameter Exception for stopping taskID $it")
                null
            }
        }
        log.info("Waiting for tasks to be stopped: $stoppingTaskIDs")
        // Maximum number of tasks per ecsClient waiter is 100.
        // Hence, we need to chunk the large task lists into smaller lists before requesting wait
        // Note although the waiters runs one by one, the loop latency should be small
        // as the stop task requests have been sent out previously.
        val stoppedTaskIDs = stoppingTaskIDs
            .chunked(MAX_TASK_PER_WAIT_TASK)
            .flatMap { chunkedTaskIDList ->
                val waiterResponse = try {
                    ecsClient.waiter().waitUntilTasksStopped(
                        { builder ->
                            builder.cluster(ecsClusterName)
                                .tasks(chunkedTaskIDList)
                                .build()
                        },
                        { builder ->
                            builder.waitTimeout(waiterTimeout).maxAttempts(waiterRetries).build()
                        }
                    )
                } catch (ex: UnsupportedOperationException) {
                    throw EcsTaskManagerException("UnsupportedOperationException raised while waiting for all tasks stopping", ex)
                } catch (ex: SdkClientException) {
                    throw EcsTaskManagerException("SdkClientException raised while waiting for tasks $chunkedTaskIDList stopping", ex)
                }
                // Check waiter response whether task last status is STOPPED
                waiterResponse.matched().response().get().tasks().filter {
                    // only include task shown last status as STOPPED
                    it.lastStatus() == "STOPPED" &&
                        // Filter by target taskIDList in case response contains non target task ID
                        taskIDList.contains(it.taskArn().substringAfterLast('/'))
                }.map {
                    it.taskArn().substringAfterLast('/')
                }
            }
        // Check whether we have stopping tasks that fail to end up as STOPPED
        val failToStopTaskIDs = stoppingTaskIDs - stoppedTaskIDs.toSet()
        return taskIDList - failToStopTaskIDs.toSet()
    }
}
