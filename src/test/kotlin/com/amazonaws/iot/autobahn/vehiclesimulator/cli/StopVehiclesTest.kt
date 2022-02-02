package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import com.amazonaws.iot.autobahn.vehiclesimulator.ecs.EcsController
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class StopVehiclesTest {

    private val ecsController = mockk<EcsController>()
    private val command = StopVehicles(ecsController)

    @Test
    fun call() {
        command.taskID = "test-task-id"

        every {
            ecsController.stopTask(any())
        } returns 0

        val result = command.call()

        Assertions.assertEquals(0, result)
        verify(exactly = 1) { ecsController.stopTask(command.taskID) }
    }
}
