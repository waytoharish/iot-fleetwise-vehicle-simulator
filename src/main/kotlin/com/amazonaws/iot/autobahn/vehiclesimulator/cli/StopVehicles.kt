package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import com.amazonaws.iot.autobahn.vehiclesimulator.ecs.EcsTaskManager
import picocli.CommandLine
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ecs.EcsClient
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "StopVehicles",
    description = ["Stop Virtual Vehicles and start simulation"],
)
class StopVehicles(private val ecsTaskManager: EcsTaskManager) : Callable<Int> {

    constructor() : this(
        EcsTaskManager(EcsClient.builder().region(Region.US_WEST_2).build())
    )

    @CommandLine.Option(required = true, names = ["--taskID", "-t"])
    lateinit var taskArnList: Array<String>

    override fun call(): Int {
        val result = ecsTaskManager.stopTasks(taskArnList)
        if (result == 0) {
            println("vehicles terminated!")
        } else {
            println("Failed to terminate vehicles")
        }
        return result
    }
}
