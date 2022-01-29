package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import com.amazonaws.iot.autobahn.vehiclesimulator.ecs.EcsController
import picocli.CommandLine
import picocli.CommandLine.Command
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ecs.EcsClient
import java.util.concurrent.Callable

@Command(
    name = "LaunchVehicles",
    description = ["Launch Virtual Vehicles and start simulation"],
)
class LaunchVehicles(private val ecsController: EcsController) : Callable<Int> {

    constructor() : this(
        EcsController(EcsClient.builder().region(Region.US_WEST_2).build())
    )

    @CommandLine.Option(required = true, names = ["--simulation-package-url", "-s"])
    lateinit var simulationPackageUrl: String

    override fun call(): Int {
        ecsController.getClusterLists()
        val taskArn = ecsController.runTask(simulationPackageUrl)
        println("vehicle launched! Task arn: $taskArn")
        return 0
    }
}
