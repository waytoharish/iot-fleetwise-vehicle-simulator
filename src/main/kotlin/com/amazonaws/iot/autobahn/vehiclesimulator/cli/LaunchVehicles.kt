package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import com.amazonaws.iot.autobahn.vehiclesimulator.ecs.EcsTaskManager
import picocli.CommandLine
import picocli.CommandLine.Command
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ecs.EcsClient
import java.util.concurrent.Callable

@Command(
    name = "LaunchVehicles",
    description = ["Launch Virtual Vehicles and start simulation"],
)
class LaunchVehicles(private val ecsTaskManager: EcsTaskManager) : Callable<Int> {

    constructor() : this(
        EcsTaskManager(EcsClient.builder().region(Region.US_WEST_2).build())
    )

    @CommandLine.Option(required = true, names = ["--simulation-package-url", "-s"])
    lateinit var simulationPackageUrl: String

    override fun call(): Int {
        val taskArnList = ecsTaskManager.runTasks(simulationPackageUrl)
        println("vehicle launched!")
        taskArnList.forEach {
            println("$it")
        }
        return 0
    }
}
