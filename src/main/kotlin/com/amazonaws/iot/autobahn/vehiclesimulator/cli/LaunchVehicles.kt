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
        // TODO: Ingest simulation definition from input to generate map of vehicle ID to simulation package url.
        // Below hard code map is temporarily in absence of module above
        val simulationMapping = mapOf<String, String>(
            "kfarm_v2_poc_car1" to "s3://fwe-simulator-poc/simulation/car1",
            "kfarm_v2_poc_car2" to "s3://fwe-simulator-poc/simulation/car2",
            "kfarm_v2_poc_car3" to "s3://fwe-simulator-poc/simulation/car3",
            "kfarm_v2_poc_car4" to "s3://fwe-simulator-poc/simulation/car4",
        )
        val taskArnList = ecsTaskManager.runTasks(simulationMapping)
        taskArnList.forEach {
            println("Task created: $it")
        }
        println("Vehicles launched with simulation package: $simulationPackageUrl")
        return 0
    }
}
