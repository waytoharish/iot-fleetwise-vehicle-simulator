package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import com.amazonaws.iot.autobahn.vehiclesimulator.ecs.EcsTaskManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class StopVehiclesTest {

    private val ecsTaskManager = mockk<EcsTaskManager>()
    private val command = StopVehicles(ecsTaskManager)

    @Test
    fun call() {
        command.taskArnList = arrayOf("test-task-id")

        every {
            ecsTaskManager.stopTasks(any())
        } returns 0

        val result = command.call()

        Assertions.assertEquals(0, result)
        verify(exactly = 1) { ecsTaskManager.stopTasks(command.taskArnList) }
    }
}
