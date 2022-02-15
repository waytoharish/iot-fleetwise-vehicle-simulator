package com.amazonaws.iot.autobahn.vehiclesimulator.ecs

import com.amazonaws.iot.autobahn.vehiclesimulator.exceptions.EcsTaskManagerException
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.AccessDeniedException
import software.amazon.awssdk.services.ecs.model.CapacityProviderStrategyItem
import software.amazon.awssdk.services.ecs.model.ClusterNotFoundException
import software.amazon.awssdk.services.ecs.model.ContainerOverride
import software.amazon.awssdk.services.ecs.model.InvalidParameterException
import software.amazon.awssdk.services.ecs.model.KeyValuePair
import software.amazon.awssdk.services.ecs.model.RunTaskRequest
import software.amazon.awssdk.services.ecs.model.TaskOverride
import java.time.Duration

/**
 * ECS Task Manager is responsible for start / stop ECS tasks to meet client's simulation request
 *
 */
class EcsTaskManager(
    private val ecsClient: EcsClient,
    private val ecsClusterName: String = "default"
) {
    companion object {
        // Below we define the CPU and Memory allocation for each FWE Process. Note this part are subject to future changes
        // CPU allocated per FWE process. Each container can have 1024 cpu per core
        private const val CPU_PER_FWE_PROCESS: Int = 128
        // Memory allocated per FWE process in MiB
        private const val MEM_PER_FWE_PROCESS: Int = 64

        // Per ECS service quota, only 10 tasks can be requested through runTask API. Hence, if we request to start
        // more tasks than 10, we need to loop through
        const val MAX_TASK_PER_RUN_TASK_REQUEST = 10
        // Per ECS service quota, only 100 tasks can be requested through waiter API
        const val MAX_TASK_PER_WAIT_TASK = 100
    }

    /**
     * This function interacts with ECS to run tasks hosting virtual vehicles.
     * The Key Value Pair is passed through container environment variable.
     * The function won't return until task last status is running or timeout or exception
     *
     * Note this function assume cluster and task definition has been set up previously
     *
     * @param vehicleSimulationMap: A map containing Vehicle ID to Simulation URL.
     *                              e.g: car1: S3://simulation/car1
     * @param ecsTaskDefinition: Name of task definition
     * @param ecsContainerName: Name of Container
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
        vehicleSimulationMap: Map<String, String>,
        ecsTaskDefinition: String = "fwe-with-cloudwatch-logging:7",
        ecsContainerName: String = "fwe-container",
        useCapacityProvider: Boolean = true,
        ecsLaunchType: String = "EC2",
        ecsCapacityProviderName: String = "fwe_simulator_ubuntu",
        waiterTimeout: Duration = Duration.ofMinutes(5),
        waiterRetries: Int = 100
    ): List<String> {
        // Here we create Task per Vehicle.
        val pendingTaskArnList = vehicleSimulationMap.flatMap { vehicleIDToSimUrl ->
            // We pass the vehicle ID and simulation URL through environment variable.
            val envList = listOf<KeyValuePair>(
                KeyValuePair.builder().name("VEHICLE_ID").value(vehicleIDToSimUrl.key).build(),
                KeyValuePair.builder().name("SIM_PKG_URL").value(vehicleIDToSimUrl.value).build()
            )
            val containerOverride = ContainerOverride.builder()
                .environment(envList)
                .name(ecsContainerName)
                .cpu(CPU_PER_FWE_PROCESS)
                .memory(MEM_PER_FWE_PROCESS)
                .build()
            // Build the task override
            val taskOverride = TaskOverride.builder()
                .containerOverrides(listOf(containerOverride))
                .cpu(CPU_PER_FWE_PROCESS.toString())
                .memory(MEM_PER_FWE_PROCESS.toString())
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
                .count(1)
                .taskDefinition(ecsTaskDefinition)
                .overrides(taskOverride)
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
                taskArnList
            } else {
                throw EcsTaskManagerException("RunTaskResponse contains ${taskArnList.size} tasks: $taskArnList")
            }
        }
        // TODO: logging framework will be integrated separately
        println("starting Task: $pendingTaskArnList")
        println("wait for all tasks running")
        // Maximum number of tasks per ecsClient waiter is 100.
        // Hence, we need to chunk the large task lists into smaller lists before requesting wait
        // Note although the waiters runs one by one, the loop latency should be small
        // as all the tasks have already been created by runTask in previous step.
        return pendingTaskArnList
            .chunked(MAX_TASK_PER_WAIT_TASK)
            .flatMap {
                val waiterResponse = try {
                    ecsClient.waiter().waitUntilTasksRunning(
                        { builder ->
                            builder.cluster(ecsClusterName)
                                .tasks(it)
                                .build()
                        },
                        { builder -> builder.waitTimeout(waiterTimeout).maxAttempts(waiterRetries).build() }
                    )
                } catch (ex: UnsupportedOperationException) {
                    throw EcsTaskManagerException("UnsupportedOperationException raised while waiting for all tasks running", ex)
                } catch (ex: SdkClientException) {
                    throw EcsTaskManagerException("SdkClientException raised while waiting for all tasks running", ex)
                }
                // We only return the Task Arns that are successfully running
                waiterResponse.matched().response().get().tasks().filter {
                    it.lastStatus() == "RUNNING"
                }.map { it.taskArn() }
            }
    }

    /**
     * This function take task ID as input. It will interact with ECS to stop the task.
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
                // Note if one taskID couldn't be found, it will raise this exception. We shall continue the rest
                // of stopTask request as we want to clean up the tasks as much as we can
                // In the end of function, we will throw exception if not all tasks are stopped
                // TODO Logging as Warning
                println("Invalid Parameter Exception for stopping taskID $it")
                null
            }
        }
        println("wait for all tasks to be stopped")
        // Maximum number of tasks per ecsClient waiter is 100.
        // Hence, we need to chunk the large task lists into smaller lists before requesting wait
        // Note although the waiters runs one by one, the loop latency should be small
        // as the stop task requests have been sent out previously.
        val stoppedTaskIDs = stoppingTaskIDs
            .chunked(MAX_TASK_PER_WAIT_TASK)
            .flatMap {
                val waiterResponse = try {
                    ecsClient.waiter().waitUntilTasksStopped(
                        { builder ->
                            builder.cluster(ecsClusterName)
                                .tasks(it)
                                .build()
                        },
                        { builder ->
                            builder.waitTimeout(waiterTimeout).maxAttempts(waiterRetries).build()
                        }
                    )
                } catch (ex: UnsupportedOperationException) {
                    throw EcsTaskManagerException("UnsupportedOperationException raised while waiting for all tasks stopping", ex)
                } catch (ex: SdkClientException) {
                    throw EcsTaskManagerException("SdkClientException raised while waiting for all tasks stopping", ex)
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
        // Check whether we have tasks that fail to stop
        val failToStopTaskIDs = taskIDList.filterNot { stoppedTaskIDs.contains(it) }
        if (failToStopTaskIDs.isNotEmpty()) {
            // throw exception with a list of fail to stop tasks
            throw EcsTaskManagerException("Fail to stop task ID: $failToStopTaskIDs")
        }
        return stoppedTaskIDs
    }
}
