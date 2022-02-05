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
        verify(exactly = 1) { anyConstructed<EcsTaskManager>().runTasks(command.simulationPackageUrl) }
    }
}
