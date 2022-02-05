package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import com.amazonaws.iot.autobahn.vehiclesimulator.ecs.EcsTaskManager
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class StopVehiclesTest {

    private val command = StopVehicles()

    @Test
    fun `when call StopVehicles returned with all expected tasks stopped`() {
        command.taskIDList = listOf("task1", "task2", "task3")
        command.region = "us-west-2"

        mockkConstructor(EcsTaskManager::class)

        every {
            anyConstructed<EcsTaskManager>().stopTasks(any())
        } returns listOf("task1", "task2", "task3")

        val result = command.call()

        Assertions.assertEquals(0, result)
        verify(exactly = 1) { anyConstructed<EcsTaskManager>().stopTasks(command.taskIDList) }
    }

    @Test
    fun `when call StopVehicles returned with less than expected tasks stopped`() {
        command.taskIDList = listOf("task1", "task2", "task3")
        command.region = "us-west-2"

        mockkConstructor(EcsTaskManager::class)

        every {
            anyConstructed<EcsTaskManager>().stopTasks(any())
        } returns listOf("task1", "task2")

        val result = command.call()

        Assertions.assertEquals(-1, result)
        verify(exactly = 1) { anyConstructed<EcsTaskManager>().stopTasks(command.taskIDList) }
    }
}
