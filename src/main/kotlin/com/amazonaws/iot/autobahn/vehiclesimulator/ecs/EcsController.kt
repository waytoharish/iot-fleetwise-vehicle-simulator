package com.amazonaws.iot.autobahn.vehiclesimulator.ecs

import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.ContainerOverride
import software.amazon.awssdk.services.ecs.model.KeyValuePair
import software.amazon.awssdk.services.ecs.model.TaskOverride
import kotlin.math.exp

private const val ECS_CLUSTER_NAME = "default"

private const val ECS_TASK_DEFINITION = "fwe-multi-process"
// Maximum retries for exponential backoff algorithm
// Maximum wait time is 2 ** 0 + 2 ** 1 + ... 2 ** (MAX_RETRIES - 1)
// if MAX_RETRIES is 9, max wait time is 2 ** 9 - 1 = 511 seconds
private const val MAX_RETRIES: Int = 9

private const val ECS_LAUNCH_TYPE = "EC2"

/**
 *
 * Kinesis Consumer code that ingests messages from Kaleidoscope Edge, processes those messages and publishes to
 * Timestream
 *
 *
 */
class EcsController(private var ecsClient: EcsClient) {

    public fun getClusterLists() {
        val response = ecsClient.listClusters()
        response.clusterArns().forEach { arn ->
            println("cluster arn is $arn")
        }
    }

    /**
     * This function take simulation package s3 url as input. It will interact with ECS to run the task.
     * The function won't return until task last status is running or timeout or thrown exception
     * The function return taskArn if task successfully started or empty string if failed.
     *
     * Note this function assume cluster and task definition has been setup previously
     */
    public fun runTask(simulation_package_url: String): String {
        var response = ""
        var envList = mutableListOf<KeyValuePair>(KeyValuePair.builder().name("SIM_PKG_URL").value(simulation_package_url).build())
        var containerOverride = ContainerOverride.builder().environment(envList).name("fwe-container").build()
        var taskOverride = TaskOverride.builder().containerOverrides(mutableListOf(containerOverride)).build()
        try {
            val runTaskResponse = ecsClient.runTask { builder ->
                builder.cluster(ECS_CLUSTER_NAME)
                    .count(1)
                    .taskDefinition(ECS_TASK_DEFINITION)
                    .launchType(ECS_LAUNCH_TYPE)
                    .overrides(taskOverride)
                    .build()
            }
            if (runTaskResponse.tasks().size == 1) {
                val taskArn = runTaskResponse.tasks()[0].taskArn()
                // Wait until task is running.
                // Use exponential backoff algorithm for waiting
                var retries: Int = 0
                var retry = true
                do {
                    try {
                        val taskResponse = ecsClient.describeTasks { builder ->
                            builder.cluster(ECS_CLUSTER_NAME)
                                .tasks(mutableListOf(taskArn))
                                .build()
                        }
                        val taskStatus = taskResponse.tasks()[0].lastStatus()
                        if (taskStatus == "RUNNING") {
                            retry = false
                            // ECS task is running, respond client with taskArn
                            response = taskArn
                        } else if (taskStatus == "STOPPED") {
                            // task failed to start, abort the retry loop
                            retry = false
                            println("Failed to start task")
                        }
                        // sleep for 2 ** retries seconds
                        Thread.sleep(exp(retries.toDouble()).toLong() * 1000)
                        retries++
                        println(taskStatus)
                    } catch (ex: Exception) {
                        // abort retry loop if API call throw exception
                        retry = false
                    }
                } while (retry and (retries < MAX_RETRIES))
            } else {
                // the runTaskResponse contains more than one task which is not expected
                println("Failed to start task")
                println(runTaskResponse.toString())
            }
        } catch (ex: Exception) {}
        return response
    }

    /**
     * This function take task ARN as input. It will interact with ECS to stop the task.
     * The function won't return until task last status is stopped or timeout or thrown exception
     * The function return 0 for success, -1 for failure
     *
     * Note this function assume cluster has been setup previously
     */
    public fun stopTask(taskArn: String): Int {
        var retVal: Int = -1
        try {
            val stopTaskResponse = ecsClient.stopTask { builder ->
                builder.cluster(ECS_CLUSTER_NAME)
                    .task(taskArn)
                    .reason("stop the task")
            }
            // Wait until task is stopped.
            // Use exponential backoff algorithm for waiting
            var retries: Int = 0
            var retry = true
            do {
                try {
                    val taskResponse = ecsClient.describeTasks { builder ->
                        builder.cluster(ECS_CLUSTER_NAME)
                            .tasks(mutableListOf(taskArn))
                            .build()
                    }
                    val taskStatus = taskResponse.tasks()[0].lastStatus()
                    if (taskStatus == "STOPPED") {
                        // task stopped, exit the retry loop
                        retry = false
                        retVal = 0
                    }
                    // sleep for 2 ** retries seconds
                    Thread.sleep(exp(retries.toDouble()).toLong() * 1000)
                    retries++
                    println(taskStatus)
                } catch (ex: Exception) {
                    // abort retry loop if API call throw exception
                    retry = false
                }
            } while (retry and (retries < MAX_RETRIES))
        } catch (ex: Exception) {}
        return retVal
    }
}
