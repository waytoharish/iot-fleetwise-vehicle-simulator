package com.amazonaws.iot.autobahn.vehiclesimulator.ecs

import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.ContainerOverride
import software.amazon.awssdk.services.ecs.model.KeyValuePair
import software.amazon.awssdk.services.ecs.model.TaskOverride

class EcsController(private var ecsClient: EcsClient) {

    public fun getClusterLists() {
        val response = ecsClient.listClusters()
        response.clusterArns().forEach { arn ->
            println("cluster arn is $arn")
        }
    }

    public fun runTask(simulation_package_url: String): String {
        println("simulation package url: $simulation_package_url")
        var envList = mutableListOf<KeyValuePair>(KeyValuePair.builder().name("SIM_PKG_URL").value(simulation_package_url).build())
        var containerOverride = ContainerOverride.builder().environment(envList).name("fwe-container").build()
        var taskOverride = TaskOverride.builder().containerOverrides(mutableListOf(containerOverride)).build()
        val response = ecsClient.runTask { builder ->
            builder.cluster("default")
                .count(1)
                .taskDefinition("fwe-multi-process")
                .launchType("EC2")
                .overrides(taskOverride)
                .build()
        }
        return response.tasks()[0].taskArn()
    }

    public fun stopTask(taskArn: String) {
        ecsClient.stopTask { builder ->
            builder.cluster("default")
                .task(taskArn)
                .reason("stop the task")
        }
    }
}
