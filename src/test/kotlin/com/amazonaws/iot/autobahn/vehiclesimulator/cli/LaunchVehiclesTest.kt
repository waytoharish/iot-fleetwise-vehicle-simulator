package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import com.amazonaws.iot.autobahn.vehiclesimulator.ecs.EcsTaskManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class LaunchVehiclesTest {

    private val ecsTaskManager = mockk<EcsTaskManager>()
    private val command = LaunchVehicles(ecsTaskManager)

    @Test
    fun call() {
        command.simulationPackageUrl = "simulation-package-test-url"

        every {
            ecsTaskManager.runTasks(any())
        } returns arrayOf("Task1", "Task2")

        val result = command.call()

        Assertions.assertEquals(0, result)
        verify(exactly = 1) { ecsTaskManager.runTasks(command.simulationPackageUrl) }
    }
}
