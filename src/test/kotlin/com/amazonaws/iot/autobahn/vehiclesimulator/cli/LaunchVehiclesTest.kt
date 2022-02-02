package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import com.amazonaws.iot.autobahn.vehiclesimulator.ecs.EcsController
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class LaunchVehiclesTest {

    private val ecsController = mockk<EcsController>()
    private val command = LaunchVehicles(ecsController)

    @Test
    fun call() {
        command.simulationPackageUrl = "simulation-package-test-url"

        every {
            ecsController.runTask(any())
        } returns "launched"

        val result = command.call()

        Assertions.assertEquals(0, result)
        verify(exactly = 1) { ecsController.runTask(command.simulationPackageUrl) }
    }
}
