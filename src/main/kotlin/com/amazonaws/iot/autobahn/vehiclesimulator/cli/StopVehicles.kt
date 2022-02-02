package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import com.amazonaws.iot.autobahn.vehiclesimulator.ecs.EcsController
import picocli.CommandLine
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ecs.EcsClient
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "StopVehicles",
    description = ["Stop Virtual Vehicles and start simulation"],
)
class StopVehicles(private val ecsController: EcsController) : Callable<Int> {

    constructor() : this(
        EcsController(EcsClient.builder().region(Region.US_WEST_2).build())
    )

    @CommandLine.Option(required = true, names = ["--taskID", "-t"])
    lateinit var taskID: String

    override fun call(): Int {
        val result = ecsController.stopTask(taskID)
        if (result == 0) {
            println("vehicles terminated!")
        } else {
            println("Failed to terminate vehicles")
        }
        return result
    }
}
