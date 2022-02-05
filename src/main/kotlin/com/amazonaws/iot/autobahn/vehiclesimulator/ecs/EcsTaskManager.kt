package com.amazonaws.iot.autobahn.vehiclesimulator.ecs

import com.amazonaws.iot.autobahn.vehiclesimulator.exceptions.EcsTaskManagerException
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
    private var ecsClient: EcsClient,
    private val ecsClusterName: String = "default",
    private val ecsLaunchType: String = "EC2",
    private val ecsTaskDefinition: String = "fwe-multi-process",
    private val ecsContainerName: String = "fwe-container",
    private val ecsEnvName: String = "SIM_PKG_URL"
) {

    companion object {
        // maximum available CPU and Memory to one Task. Below data is based on m6i.xlarge
        // If client request more vehicles than a maximum task can handle, the program will break it down.
        private const val MAX_CPU_PER_TASK = 4096
        private const val MAX_MEM_PER_TASK = 15736

        // Below we define the CPU and Memory allocation for each FWE Process. Note this part are subject to future changes
        // CPU allocated per FWE process. Each container can have 1024 cpu per core
        private const val CPU_PER_FWE_PROCESS: Int = 16
        // Memory allocated per FWE process in MiB
        private const val MEM_PER_FWE_PROCESS: Int = 64

        private const val MAX_NUM_OF_VEHICLES_PER_TASK = MAX_MEM_PER_TASK / MEM_PER_FWE_PROCESS
    }

    /**
     * This function interacts with ECS to run the task.
     * It takes simulation package s3 url
     * The function won't return until task last status is running or timeout or thrown exception
     * The function return a list of taskArn of running tasks
     *
     * Note this function assume cluster and task definition has been setup previously
     */
    public fun runTasks(simulationPackageUrl: String): List<String> {
        // TODO: Add Sanity check on simulation package url.
        // First, we need to calculate how much CPU and Memory to be allocated to tasks
        val (taskCount, taskCpu, taskMemory) = calculateComputingResource(processSimulationPackages(simulationPackageUrl))
        println("task count: $taskCount, each task cpu: $taskCpu, each task memory: $taskMemory")

        val envList = mutableListOf<KeyValuePair>(KeyValuePair.builder().name(ecsEnvName).value(simulationPackageUrl).build())
        val containerOverride = ContainerOverride.builder().environment(envList).name(ecsContainerName).cpu(taskCpu).memory(taskMemory).build()
        val taskOverride = TaskOverride.builder().containerOverrides(mutableListOf(containerOverride)).cpu(taskCpu.toString()).memory(taskMemory.toString()).build()

        val pendingTaskArnList =
            try {
                ecsClient.runTask { builder ->
                    builder.cluster(ecsClusterName)
                        .count(taskCount)
                        .taskDefinition(ecsTaskDefinition)
                        .launchType(ecsLaunchType)
                        .overrides(taskOverride)
                        .build()
                }.tasks().map { it.taskArn() }.toMutableList()
            } catch (ex: ClusterNotFoundException) {
                throw EcsTaskManagerException("Cluster Not Found Exception", ex)
            } catch (ex: InvalidParameterException) {
                throw EcsTaskManagerException("Invalid Parameter Exception", ex)
            } catch (ex: AccessDeniedException) {
                throw EcsTaskManagerException("Access Denied Exception", ex)
            }
        println("wait for all tasks running")
        val waiterResponse = try {
            ecsClient.waiter().waitUntilTasksRunning(
                { builder ->
                    builder.cluster(ecsClusterName)
                        .tasks(pendingTaskArnList)
                        .build()
                },
                { builder -> builder.waitTimeout(Duration.ofMinutes(5)).build() }
            )
        } catch (ex: UnsupportedOperationException) {
            throw EcsTaskManagerException("UnsupportedOperationException raised while waiting for all tasks running")
        }
        return waiterResponse.matched().response().get().tasks().filter {
            it.lastStatus() == "RUNNING"
        }.map { it.taskArn() }
    }

    /**
     * This function take task ID as input. It will interact with ECS to stop the task.
     * The function won't return until task last status is stopped or timeout or thrown exception
     * The function return list of stopped tasks
     *
     * Note this function assume cluster has been setup previously
     */
    public fun stopTasks(taskIDList: List<String>): List<String> {
        taskIDList.forEach {
            try {
                val stopTaskResponse = ecsClient.stopTask { builder ->
                    builder.cluster(ecsClusterName)
                        .task(it)
                        .reason("stop the task")
                }
                print(stopTaskResponse.toString())
            } catch (ex: ClusterNotFoundException) {
                throw EcsTaskManagerException("Cluster Not Found Exception for stopping taskID $it", ex)
            } catch (ex: InvalidParameterException) {
                // Note if the task couldn't be found, it will raise this exception
                throw EcsTaskManagerException("Invalid Parameter Exception for stopping taskID $it", ex)
            }
        }
        // list of task to be stopped
        val toBeStoppedTaskIDs = taskIDList.toMutableList()
        println("wait for all tasks to be stopped")
        val waiterResponse = try {
            ecsClient.waiter().waitUntilTasksStopped(
                { builder ->
                    builder.cluster(ecsClusterName)
                        .tasks(toBeStoppedTaskIDs)
                        .build()
                },
                { builder ->
                    builder.waitTimeout(Duration.ofMinutes(5)).build()
                }
            )
        } catch (ex: UnsupportedOperationException) {
            throw EcsTaskManagerException("UnsupportedOperationException raised while waiting for all tasks stopping")
        }
        val stoppedTaskIDList = mutableListOf<String>()
        waiterResponse.matched().response().get().tasks().filter {
            taskIDList.contains(it.taskArn().substringAfterLast('/'))
        }.map {
            if (it.lastStatus() == "STOPPED") {
                stoppedTaskIDList.add(it.taskArn().substringAfterLast('/'))
            }
        }
        return stoppedTaskIDList
    }

    /**
     * This function take simulation package url as input and iterate through the folder structure
     * to assign group of vehicles to each task based on maximum load of task
     */
    private fun processSimulationPackages(simulationPackageUrl: String): Int {
        // TODO: Iterate through Simulation Package and divide vehicles into chunks
        //       based on maximum number of vehicles each task can handle
        return 4
    }

    /**
     * This function calculate the number of tasks and task cpu, memory allocation based on number of vehicles
     */
    private fun calculateComputingResource(numOfVehicles: Int): Triple<Int, Int, Int> {
        var taskCount: Int = numOfVehicles / MAX_NUM_OF_VEHICLES_PER_TASK + 1
        // If only one task is required, we set the task CPU, Memory based on number of vehicles
        // Otherwise, the task will use maximum CPU and Memory
        var taskCpu: Int = if (taskCount == 1) (numOfVehicles * CPU_PER_FWE_PROCESS).coerceAtLeast(128) else MAX_CPU_PER_TASK
        var taskMemory: Int = if (taskCount == 1) numOfVehicles * MEM_PER_FWE_PROCESS else MAX_MEM_PER_TASK

        return Triple(taskCount, taskCpu, taskMemory)
    }
}
