package com.amazonaws.iot.autobahn.vehiclesimulator.ecs

import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.ContainerOverride
import software.amazon.awssdk.services.ecs.model.KeyValuePair
import software.amazon.awssdk.services.ecs.model.TaskOverride
import java.lang.Math.pow

private const val ECS_CLUSTER_NAME = "default"

private const val ECS_TASK_DEFINITION = "fwe-multi-process"
// TODO Switch to capacity provider
private const val ECS_LAUNCH_TYPE = "EC2"

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

// Maximum retries for exponential backoff algorithm
// Maximum wait time is 2 ** 0 + 2 ** 1 + ... 2 ** (MAX_RETRIES - 1)
// if MAX_RETRIES is 9, max wait time is 2 ** 9 - 1 = 511 seconds
private val MAX_RETRIES: Int = 9
/**
 * ECS Task Manager is responsible for start / stop ECS tasks to meet client's simulation request
 *
 */
class EcsTaskManager(private var ecsClient: EcsClient) {

    /**
     * This function interacts with ECS to run the task.
     * It takes simulation package s3 url and number of vehicles as input
     * The function won't return until task last status is running or timeout or thrown exception
     * The function return taskArn if task successfully started or empty string if failed.
     *
     * Note this function assume cluster and task definition has been setup previously
     */
    public fun runTasks(simulationPackageUrl: String): Array<String> {
        // TODO: Add Sanity check on simulation package url.
        // First, we need to calculate how much CPU and Memory to be allocated to tasks
        val (taskCount, taskCpu, taskMemory) = calculateComputingResource(processSimulationPackages(simulationPackageUrl))
        println("task count: $taskCount, each task cpu: $taskCpu, each task memory: $taskMemory")
        var runningTaskArnList: MutableList<String> = mutableListOf()
        var envList = mutableListOf<KeyValuePair>(KeyValuePair.builder().name("SIM_PKG_URL").value(simulationPackageUrl).build())
        var containerOverride = ContainerOverride.builder().environment(envList).name("fwe-container").cpu(taskCpu).memory(taskMemory).build()
        var taskOverride = TaskOverride.builder().containerOverrides(mutableListOf(containerOverride)).cpu(taskCpu.toString()).memory(taskMemory.toString()).build()
        try {
            val runTaskResponse = ecsClient.runTask { builder ->
                builder.cluster(ECS_CLUSTER_NAME)
                    .count(taskCount)
                    .taskDefinition(ECS_TASK_DEFINITION)
                    .launchType(ECS_LAUNCH_TYPE)
                    .overrides(taskOverride)
                    .build()
            }
            println(runTaskResponse.toString())
            if (runTaskResponse.tasks().size == taskCount) {
                var pendingTaskArnList = runTaskResponse.tasks().map { it.taskArn() }.toMutableList()
                // Wait until all tasks are running.
                // Use exponential backoff algorithm for waiting
                var retries: Int = 0
                var retry = true
                do {
                    try {
                        val taskResponse = ecsClient.describeTasks { builder ->
                            builder.cluster(ECS_CLUSTER_NAME)
                                .tasks(pendingTaskArnList)
                                .build()
                        }
                        // clear the pending task list first
                        // It will be refilled next by iterating task response
                        pendingTaskArnList.clear()
                        taskResponse.tasks().forEach {
                            val taskStatus = it.lastStatus()
                            if (taskStatus == "RUNNING") {
                                // This task is running, add it to the runningTaskList
                                runningTaskArnList.add(it.taskArn())
                            } else if (taskStatus == "STOPPED") {
                                // task failed to start, abort the retry loop
                                retry = false
                                println("Failed to start task ${it.taskArn()}")
                            } else {
                                // This task is NOT running or stopped, add it to the pending task list
                                pendingTaskArnList.add(it.taskArn())
                            }
                            println("${it.taskArn()} : ${it.lastStatus()}")
                        }
                        // Check if there's any pending tasks
                        if (pendingTaskArnList.isEmpty()) {
                            retry = false
                        }
                        if (retry) {
                            // sleep for 2 ** retries seconds
                            println("sleep for ${pow(2.toDouble(), retries.toDouble()).toLong()} seconds")
                            Thread.sleep(pow(2.toDouble(), retries.toDouble()).toLong() * 1000)
                            retries++
                        }
                    } catch (ex: Exception) {
                        // abort retry loop if API call throw exception
                        retry = false
                        println("describeTasks exception: $ex")
                    }
                } while (retry and (retries < MAX_RETRIES))
            } else {
                // the runTaskResponse contains invalid number of tasks
                println("runTaskResponse contains invalid number of tasks")
                println(runTaskResponse.toString())
            }
        } catch (ex: Exception) {
            println("runTask exception: $ex")
        }
        return runningTaskArnList.toTypedArray()
    }

    /**
     * This function take task ID as input. It will interact with ECS to stop the task.
     * The function won't return until task last status is stopped or timeout or thrown exception
     * The function return 0 for success, non-zero for failure
     *
     * Note this function assume cluster has been setup previously
     */
    public fun stopTasks(taskIDList: Array<String>): Int {
        var retVal: Int = -1
        taskIDList.forEach {
            try {
                ecsClient.stopTask { builder ->
                    builder.cluster(ECS_CLUSTER_NAME)
                        .task(it)
                        .reason("stop the task")
                }
            } catch (ex: Exception) {
                println("stopTask exception: $ex")
            }
        }
        // Wait until task is stopped.
        // Use exponential backoff algorithm for waiting
        var retries: Int = 0
        var retry = true
        // list of task to be stopped
        var toBeStoppedTaskIDs = taskIDList.toMutableList()
        do {
            try {
                val describeTasksResponse = ecsClient.describeTasks { builder ->
                    builder.cluster(ECS_CLUSTER_NAME)
                        .tasks(toBeStoppedTaskIDs)
                        .build()
                }
                // clear the list and only add back task that are NOT stopped
                toBeStoppedTaskIDs.clear()
                // iterate through task response to check task status
                describeTasksResponse.tasks().forEach {
                    if (it.lastStatus() != "STOPPED") {
                        toBeStoppedTaskIDs.add(it.taskArn().substringAfterLast('/'))
                    }
                    println("${it.taskArn()}: ${it.lastStatus()}")
                }
                // Check if All Tasks have been stopped
                if (toBeStoppedTaskIDs.isEmpty()) {
                    retry = false
                    retVal = 0
                }
                if (retry) {
                    // sleep for 2 ** retries seconds
                    println("sleep for ${pow(2.toDouble(), retries.toDouble()).toLong()} seconds")
                    Thread.sleep(pow(2.toDouble(), retries.toDouble()).toLong() * 1000)
                    retries++
                }
            } catch (ex: Exception) {
                // abort retry loop if API call throw exception
                retry = false
                println("describeTasks exception: $ex")
            }
        } while (retry and (retries < MAX_RETRIES))
        return retVal
    }

    /**
     * This function take simulation package url as input and iterate through the folder structure
     * to assign group of vehicles to each task based on maximum load of task
     */
    private fun processSimulationPackages(simulationPackageUrl: String): Int {
        // TODO: Iterate through Simulation Package and divide folders into chunks
        //       based on maximum number of vehicles each task can handle
        return 4
    }

    /**
     * This function calculate the number of tasks and task cpu, memory allocation based on number of vehicles
     */
    private fun calculateComputingResource(numOfVehicles: Int): Triple<Int, Int, Int> {
        var taskCpu: Int = MAX_CPU_PER_TASK
        var taskMemory: Int = MAX_MEM_PER_TASK
        var taskCount: Int = numOfVehicles / MAX_NUM_OF_VEHICLES_PER_TASK + 1
        // If only one task is required, we set the task CPU, Memory based on number of vehicles
        // Otherwise, the task will use maximum CPU and Memory
        if (taskCount == 1) {
            taskCpu = (numOfVehicles * CPU_PER_FWE_PROCESS).coerceAtLeast(128)
            taskMemory = numOfVehicles * MEM_PER_FWE_PROCESS
        }
        return Triple(taskCount, taskCpu, taskMemory)
    }
}
