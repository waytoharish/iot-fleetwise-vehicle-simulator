package com.amazonaws.iot.autobahn.vehiclesimulator.ecs

import com.amazonaws.iot.autobahn.vehiclesimulator.exceptions.EcsTaskManagerException
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.AccessDeniedException
import software.amazon.awssdk.services.ecs.model.ClusterNotFoundException
import software.amazon.awssdk.services.ecs.model.ContainerOverride
import software.amazon.awssdk.services.ecs.model.InvalidParameterException
import software.amazon.awssdk.services.ecs.model.KeyValuePair
import software.amazon.awssdk.services.ecs.model.TaskOverride
import java.time.Duration

/**
 * ECS Task Manager is responsible for start / stop ECS tasks to meet client's simulation request
 *
 */
class EcsTaskManager(
    private val ecsClient: EcsClient,
    private val ecsClusterName: String = "default",
    private val ecsLaunchType: String = "EC2",
    private val ecsTaskDefinition: String = "fwe-with-cloudwatch-logging:7",
    private val ecsContainerName: String = "fwe-container",
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
     * [vehicleSimulationMap] is a map containing Vehicle ID to Simulation URL.
     * e.g: car1: S3://simulation/car1
     * The Key Value Pair is passed through container environment variable.
     *
     * The function won't return until task last status is running or timeout or exception
     * If no exception thrown, the function return a list of taskArn of running tasks
     *
     * Note this function assume cluster and task definition has been setup previously
     */
    public fun runTasks(
        vehicleSimulationMap: Map<String, String>,
        timeout: Duration = Duration.ofMinutes(5)
    ): List<String> {
        val pendingTaskArnList = mutableListOf<String>()
        // Here we create Task per Vehicle.
        vehicleSimulationMap.forEach { vehicleIDToSimUrl ->
            // We pass the vehicle ID and simulation URL through environment variable.
            val envList = mutableListOf<KeyValuePair>(
                KeyValuePair.builder().name("VEHICLE_ID").value(vehicleIDToSimUrl.key).build(),
                KeyValuePair.builder().name("SIM_PKG_URL").value(vehicleIDToSimUrl.value).build()
            )
            val containerOverride = ContainerOverride.builder()
                .environment(envList)
                .name(ecsContainerName)
                .cpu(CPU_PER_FWE_PROCESS)
                .memory(MEM_PER_FWE_PROCESS)
                .build()
            val taskOverride = TaskOverride.builder()
                .containerOverrides(mutableListOf(containerOverride))
                .cpu(CPU_PER_FWE_PROCESS.toString())
                .memory(MEM_PER_FWE_PROCESS.toString())
                .build()
            val taskArnList = try {
                ecsClient.runTask { builder ->
                    builder.cluster(ecsClusterName)
                        .count(1)
                        .taskDefinition(ecsTaskDefinition)
                        .launchType(ecsLaunchType)
                        .overrides(taskOverride)
                        .build()
                }.tasks().map { it.taskArn() }
            } catch (ex: ClusterNotFoundException) {
                throw EcsTaskManagerException("Cluster Not Found Exception", ex)
            } catch (ex: InvalidParameterException) {
                throw EcsTaskManagerException("Invalid Parameter Exception", ex)
            } catch (ex: AccessDeniedException) {
                throw EcsTaskManagerException("Access Denied Exception", ex)
            }
            if (taskArnList.size == 1) {
                pendingTaskArnList.add(taskArnList[0])
            } else {
                throw EcsTaskManagerException("RunTaskResponse contains ${taskArnList.size} tasks: $taskArnList")
            }
        }
        // TODO: logging framework will be integrated separately
        println("starting Task: $pendingTaskArnList")
        println("wait for all tasks running")
        val runningTaskArnList = mutableListOf<String>()
        // Maximum number of tasks per ecsClient waiter is 100.
        // Hence, we need to chunk the large task lists into smaller lists before requesting wait
        // Note although the waiters runs one by one, the loop latency should be small
        // as all the tasks have already been created by runTask in previous step.
        pendingTaskArnList
            .chunked(MAX_TASK_PER_WAIT_TASK)
            .forEach {
                val waiterResponse = try {
                    ecsClient.waiter().waitUntilTasksRunning(
                        { builder ->
                            builder.cluster(ecsClusterName)
                                .tasks(it)
                                .build()
                        },
                        { builder -> builder.waitTimeout(timeout).build() }
                    )
                } catch (ex: UnsupportedOperationException) {
                    throw EcsTaskManagerException("UnsupportedOperationException raised while waiting for all tasks running", ex)
                } catch (ex: SdkClientException) {
                    throw EcsTaskManagerException("SdkClientException raised while waiting for all tasks running", ex)
                }
                // We only return the Task Arns that are successfully running
                runningTaskArnList += waiterResponse.matched().response().get().tasks().filter {
                    it.lastStatus() == "RUNNING"
                }.map { it.taskArn() }
                println("${runningTaskArnList.size} tasks are running")
            }
        return runningTaskArnList
    }

    /**
     * This function take task ID as input. It will interact with ECS to stop the task.
     * The function won't return until task last status is stopped or timeout or thrown exception
     * The function return list of stopped tasks
     *
     * Note this function assume cluster has been setup previously
     */
    public fun stopTasks(
        taskIDList: List<String>,
        timeout: Duration = Duration.ofMinutes(5)
    ): List<String> {
        taskIDList.forEach {
            try {
                ecsClient.stopTask { builder ->
                    builder.cluster(ecsClusterName)
                        .task(it)
                        .reason("stop the task")
                }
            } catch (ex: ClusterNotFoundException) {
                throw EcsTaskManagerException("Cluster Not Found Exception for stopping taskID $it", ex)
            } catch (ex: InvalidParameterException) {
                // Note if the task couldn't be found, it will raise this exception
                throw EcsTaskManagerException("Invalid Parameter Exception for stopping taskID $it", ex)
            }
        }
        // list of task to be stopped
        val toBeStoppedTaskIDs = taskIDList.toMutableList()
        val stoppedTaskIDList = mutableListOf<String>()
        println("wait for all tasks to be stopped")
        // Maximum number of tasks per ecsClient waiter is 100.
        // Hence, we need to chunk the large task lists into smaller lists before requesting wait
        // Note although the waiters runs one by one, the loop latency should be small
        // as the stop task requests have been sent out previously.
        toBeStoppedTaskIDs
            .chunked(MAX_TASK_PER_WAIT_TASK)
            .forEach {
                val waiterResponse = try {
                    ecsClient.waiter().waitUntilTasksStopped(
                        { builder ->
                            builder.cluster(ecsClusterName)
                                .tasks(it)
                                .build()
                        },
                        { builder ->
                            builder.waitTimeout(timeout).build()
                        }
                    )
                } catch (ex: UnsupportedOperationException) {
                    throw EcsTaskManagerException("UnsupportedOperationException raised while waiting for all tasks stopping", ex)
                } catch (ex: SdkClientException) {
                    throw EcsTaskManagerException("SdkClientException raised while waiting for all tasks stopping", ex)
                }
                // Check waiter response whether task last status is STOPPED
                waiterResponse.matched().response().get().tasks().filter {
                    // We firstly filter by target taskIDList in case response contains non target task ID
                    taskIDList.contains(it.taskArn().substringAfterLast('/'))
                }.map {
                    if (it.lastStatus() == "STOPPED") {
                        // Task last status is STOPPED. Add it to return list
                        stoppedTaskIDList.add(it.taskArn().substringAfterLast('/'))
                    }
                }
            }
        return stoppedTaskIDList
    }
}
