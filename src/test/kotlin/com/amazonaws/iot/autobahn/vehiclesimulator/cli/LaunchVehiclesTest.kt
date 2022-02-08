package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import com.amazonaws.iot.autobahn.vehiclesimulator.ecs.EcsTaskManager
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class LaunchVehiclesTest {

    private val command = LaunchVehicles()

    @Test
    fun `when call LaunchVehicles with returning a list of taskID`() {
        command.simulationPackageUrl = "simulation-package-test-url"
        command.region = "us-west-2"

        mockkConstructor(EcsTaskManager::class)

        every {
            anyConstructed<EcsTaskManager>().runTasks(any())
        } returns listOf("Task1", "Task2")

        val result = command.call()

        Assertions.assertEquals(0, result)
        val simulationMapping = mapOf<String, String>(
            "kfarm_v2_poc_car1" to "s3://fwe-simulator-poc/simulation/car1",
            "kfarm_v2_poc_car2" to "s3://fwe-simulator-poc/simulation/car2",
            "kfarm_v2_poc_car3" to "s3://fwe-simulator-poc/simulation/car3",
            "kfarm_v2_poc_car4" to "s3://fwe-simulator-poc/simulation/car4",
        )
        verify(exactly = 1) { anyConstructed<EcsTaskManager>().runTasks(simulationMapping) }
    }
}
