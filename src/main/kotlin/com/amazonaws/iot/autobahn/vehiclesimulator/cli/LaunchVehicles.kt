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
class LaunchVehicles() : Callable<Int> {

    @CommandLine.Option(required = true, names = ["--simulation-package-url", "-s"])
    lateinit var simulationPackageUrl: String
    @CommandLine.Option(required = true, names = ["--region", "-r"])
    lateinit var region: String

    override fun call(): Int {
        val ecsTaskManager = EcsTaskManager(EcsClient.builder().region(Region.of(region)).build())
        val taskArnList = ecsTaskManager.runTasks(simulationPackageUrl)
        taskArnList.forEach {
            println("Task created: $it")
        }
        println("Vehicles launched with simulation package: $simulationPackageUrl")
        return 0
    }
}
