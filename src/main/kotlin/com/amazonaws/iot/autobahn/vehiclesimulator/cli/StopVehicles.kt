package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import com.amazonaws.iot.autobahn.vehiclesimulator.ecs.EcsTaskManager
import picocli.CommandLine
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ecs.EcsClient
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "StopVehicles",
    description = ["Stop Virtual Vehicles"],
)
class StopVehicles() : Callable<Int> {

    @CommandLine.Option(required = true, names = ["--taskID", "-t"])
    lateinit var taskIDList: List<String>
    @CommandLine.Option(required = true, names = ["--region", "-r"])
    lateinit var region: String

    override fun call(): Int {
        val ecsTaskManager = EcsTaskManager(EcsClient.builder().region(Region.of(region)).build())
        val stoppedTaskIDList = ecsTaskManager.stopTasks(taskIDList)
        taskIDList.filter {
            stoppedTaskIDList.contains(it)
        }.forEach {
            println("Stopped task $it")
        }
        taskIDList.filter {
            !stoppedTaskIDList.contains(it)
        }.forEach {
            println("Couldn't stop task $it")
        }
        return if (stoppedTaskIDList == taskIDList) 0 else -1
    }
}
