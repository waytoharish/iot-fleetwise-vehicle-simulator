package com.amazonaws.iot.autobahn.vehiclesimulator.ecs

import com.amazonaws.iot.autobahn.vehiclesimulator.exceptions.EcsTaskManagerException
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.core.internal.waiters.ResponseOrException.response
import software.amazon.awssdk.core.waiters.WaiterOverrideConfiguration
import software.amazon.awssdk.core.waiters.WaiterResponse
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.AccessDeniedException
import software.amazon.awssdk.services.ecs.model.ClusterNotFoundException
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse
import software.amazon.awssdk.services.ecs.model.InvalidParameterException
import software.amazon.awssdk.services.ecs.model.RunTaskRequest
import software.amazon.awssdk.services.ecs.model.RunTaskResponse
import software.amazon.awssdk.services.ecs.model.StopTaskRequest
import software.amazon.awssdk.services.ecs.model.StopTaskResponse
import software.amazon.awssdk.services.ecs.model.Task
import java.util.function.Consumer

internal class EcsTaskManagerTest {

    private val ecsClient = mockk<EcsClient>()

    private val ecsTaskManager = spyk(EcsTaskManager(ecsClient), recordPrivateCalls = true)

    private val waiter = mockk<WaiterResponse<DescribeTasksResponse>>()

    private val smallFleetSimulation = mapOf<String, String>(
        "car1" to "sim-url-for-car1",
        "car2" to "sim-url-for-car2",
        "car3" to "sim-url-for-car3",
        "car4" to "sim-url-for-car4"
    )
    // Below create a large fleet vehicle input with MAX_NUM_OF_VEHICLES_PER_TASK + 1 of vehicles
    private val largeFleetSimulation: Map<String, String> = (1..EcsTaskManager.MAX_NUM_OF_VEHICLES_PER_TASK + 1)
        .map { it.toString() }
        .zip(
            (1..EcsTaskManager.MAX_NUM_OF_VEHICLES_PER_TASK + 1)
                .map { it.toString() }
        ).toMap()

    @Test
    fun `when runTasks with invoking ecsClient to run one tasks`() {
        val expectedTaskArnList = listOf<String>("test-task-1")
        val newTasks = expectedTaskArnList.map {
            Task.builder().taskArn(it).lastStatus("RUNNING").build()
        }.toMutableList()

        every {
            waiter.attemptsExecuted()
        } returns 42
        every {
            waiter.matched()
        } returns response(DescribeTasksResponse.builder().tasks(newTasks).build())

        val runTaskRequestList = mutableListOf<Consumer<RunTaskRequest.Builder>>()

        every {
            ecsClient.runTask(capture(runTaskRequestList))
        } returns RunTaskResponse.builder().tasks(newTasks).build()

        val describeTaskRequestList = mutableListOf<Consumer<DescribeTasksRequest.Builder>>()
        val waiterOverrideConfigList = mutableListOf<Consumer<WaiterOverrideConfiguration.Builder>>()
        every {
            ecsClient.waiter().waitUntilTasksRunning(capture(describeTaskRequestList), capture(waiterOverrideConfigList))
        } returns waiter

        val returnedTaskArnList = ecsTaskManager.runTasks(smallFleetSimulation)

        val requestedTaskCount = runTaskRequestList.map {
            val builder = RunTaskRequest.builder()
            it.accept(builder)
            builder.build().count()
        }[0]
        // As there's only 4 vehicles, only 1 task will be generated
        Assertions.assertEquals(1, requestedTaskCount)

        val requestedVehicleIDList = runTaskRequestList.map {
            val builder = RunTaskRequest.builder()
            it.accept(builder)
            builder.build().overrides().containerOverrides()[0].environment()[0]
        }[0]
        // As there's only 4 vehicles, only 1 task will be generated
        Assertions.assertEquals("VEHICLE_ID_LIST", requestedVehicleIDList.name())
        Assertions.assertEquals("[car1,car2,car3,car4]", requestedVehicleIDList.value())

        val requestedSimulationUrlList = runTaskRequestList.map {
            val builder = RunTaskRequest.builder()
            it.accept(builder)
            builder.build().overrides().containerOverrides()[0].environment()[1]
        }[0]
        // As there's only 4 vehicles, only 1 task will be generated
        Assertions.assertEquals("SIM_URL_LIST", requestedSimulationUrlList.name())
        Assertions.assertEquals("[sim-url-for-car1,sim-url-for-car2,sim-url-for-car3,sim-url-for-car4]", requestedSimulationUrlList.value())

        val describeTaskRequestTaskList = describeTaskRequestList.map {
            val builder = DescribeTasksRequest.builder()
            it.accept(builder)
            builder.build().tasks()
        }[0]
        Assertions.assertEquals(expectedTaskArnList, describeTaskRequestTaskList)

        val actualWaiterOverrideConfig = waiterOverrideConfigList.map {
            val builder = WaiterOverrideConfiguration.builder()
            it.accept(builder)
            builder.build().waitTimeout()
        }[0]
        Assertions.assertEquals(5, actualWaiterOverrideConfig.get().toMinutes())

        Assertions.assertEquals(expectedTaskArnList, returnedTaskArnList)
    }

    @Test
    fun `when runTasks called with ecsClient runTask raise exceptions`() {
        val runTaskRequestList = mutableListOf<Consumer<RunTaskRequest.Builder>>()
        listOf<Exception>(
            ClusterNotFoundException.builder().build(),
            InvalidParameterException.builder().build(),
            AccessDeniedException.builder().build()
        ).map {
            every {
                ecsClient.runTask(capture(runTaskRequestList))
            } throws it
            assertThrows<EcsTaskManagerException> {
                ecsTaskManager.runTasks(smallFleetSimulation)
            }
        }
    }

    @Test
    fun `when runTasks called with ecsClient waiter raise exceptions`() {
        val expectedTaskArnList = listOf<String>("test-task-1", "test-task-2")
        val newTasks = expectedTaskArnList.map {
            Task.builder().taskArn(it).lastStatus("RUNNING").build()
        }.toMutableList()

        val runTaskRequestList = mutableListOf<Consumer<RunTaskRequest.Builder>>()
        every {
            ecsClient.runTask(capture(runTaskRequestList))
        } returns RunTaskResponse.builder().tasks(newTasks).build()

        val describeTaskRequestList = mutableListOf<Consumer<DescribeTasksRequest.Builder>>()
        val waiterOverrideConfigList = mutableListOf<Consumer<WaiterOverrideConfiguration.Builder>>()
        every {
            ecsClient.waiter().waitUntilTasksRunning(capture(describeTaskRequestList), capture(waiterOverrideConfigList))
        } throws UnsupportedOperationException()
        assertThrows<EcsTaskManagerException> {
            ecsTaskManager.runTasks(largeFleetSimulation)
        }
    }

    // This test aims to validate runTasks can handle failure case when the task list from
    // RunTaskResponse mismatch with expected task list
    @Test
    fun `when runTasks called with only partial tasks running after wait timeout`() {
        val expectedTaskArnList = listOf<String>("task1", "task2")
        val newTasks = mutableListOf<Task>(
            Task.builder().taskArn("task1").lastStatus("PENDING").build(),
            Task.builder().taskArn("task2").lastStatus("PENDING").build()
        )

        // mock Task2 never turn RUNNING
        every {
            waiter.attemptsExecuted()
        } returns 42
        every {
            waiter.matched()
        } returns response(
            DescribeTasksResponse.builder().tasks(
                listOf<Task>(
                    Task.builder().taskArn("task1").lastStatus("RUNNING").build(),
                    Task.builder().taskArn("task2").lastStatus("STOPPED").build()
                )
            ).build()
        )

        val runTaskRequestList = mutableListOf<Consumer<RunTaskRequest.Builder>>()

        every {
            ecsClient.runTask(capture(runTaskRequestList))
        } returns RunTaskResponse.builder().tasks(newTasks).build()

        val describeTaskRequestList = mutableListOf<Consumer<DescribeTasksRequest.Builder>>()
        val waiterOverrideConfigList = mutableListOf<Consumer<WaiterOverrideConfiguration.Builder>>()
        every {
            ecsClient.waiter().waitUntilTasksRunning(capture(describeTaskRequestList), capture(waiterOverrideConfigList))
        } returns waiter

        val returnedTaskArnList = ecsTaskManager.runTasks(largeFleetSimulation)
        Assertions.assertTrue(listOf<String>("task1") == returnedTaskArnList)

        val requestedTaskCount = runTaskRequestList.map {
            val builder = RunTaskRequest.builder()
            it.accept(builder)
            builder.build().count()
        }[0]
        // As there's only 4 vehicles, only 1 task will be generated
        Assertions.assertEquals(2, requestedTaskCount)

        val describeTaskRequestTaskList = describeTaskRequestList.map {
            val builder = DescribeTasksRequest.builder()
            it.accept(builder)
            builder.build().tasks()
        }[0]
        Assertions.assertEquals(expectedTaskArnList, describeTaskRequestTaskList)

        val actualWaiterOverrideConfig = waiterOverrideConfigList.map {
            val builder = WaiterOverrideConfiguration.builder()
            it.accept(builder)
            builder.build().waitTimeout()
        }[0]
        Assertions.assertEquals(5, actualWaiterOverrideConfig.get().toMinutes())
    }

    @Test
    fun `when stopTasks called with ECS tasks successfully STOPPED`() {
        val inputTaskIDList = listOf("task1", "task2", "task3", "task4", "task5")
        val taskList = inputTaskIDList.map {
            Task.builder().taskArn(it).lastStatus("STOPPED").build()
        }.toSet()

        every {
            waiter.matched()
        } returns response(DescribeTasksResponse.builder().tasks(taskList).build())

        val stopTaskRequestList = mutableListOf<Consumer<StopTaskRequest.Builder>>()
        every {
            ecsClient.stopTask(capture(stopTaskRequestList))
        } returns StopTaskResponse.builder().build()

        val describeTaskRequestList = mutableListOf<Consumer<DescribeTasksRequest.Builder>>()
        val waiterOverrideConfigList = mutableListOf<Consumer<WaiterOverrideConfiguration.Builder>>()
        every {
            ecsClient.waiter().waitUntilTasksStopped(capture(describeTaskRequestList), capture(waiterOverrideConfigList))
        } returns waiter

        val stoppedTaskIDList = ecsTaskManager.stopTasks(inputTaskIDList)
        Assertions.assertTrue(stoppedTaskIDList == inputTaskIDList)

        val actualStoppedTaskList = stopTaskRequestList.map {
            val builder = StopTaskRequest.builder()
            it.accept(builder)
            builder.build().task()
        }
        Assertions.assertEquals(inputTaskIDList, actualStoppedTaskList)

        val describeTaskRequestTaskList = describeTaskRequestList.map {
            val builder = DescribeTasksRequest.builder()
            it.accept(builder)
            builder.build().tasks()
        }
        Assertions.assertEquals(inputTaskIDList, describeTaskRequestTaskList[0])

        val actualWaiterOverrideConfig = waiterOverrideConfigList.map {
            val builder = WaiterOverrideConfiguration.builder()
            it.accept(builder)
            builder.build().waitTimeout()
        }[0]
        Assertions.assertEquals(5, actualWaiterOverrideConfig.get().toMinutes())
    }

    @Test
    fun `when stopTasks called with ecsClient stopTask raise exception`() {
        val taskIDList = listOf("task1", "task2", "task3", "task4", "task5")

        val stopTaskRequestList = mutableListOf<Consumer<StopTaskRequest.Builder>>()
        listOf<Exception>(
            ClusterNotFoundException.builder().build(),
            InvalidParameterException.builder().build()
        ).map {
            every {
                ecsClient.stopTask(capture(stopTaskRequestList))
            } throws it
            assertThrows<EcsTaskManagerException> {
                ecsTaskManager.stopTasks(taskIDList)
            }
        }
    }

    @Test
    fun `when stopTasks called with only partial ECS tasks STOPPED`() {
        val inputTaskIDList = listOf("task1", "task2", "task3", "task4", "task5")

        // Here we mock the wait response only contains partial task IDs
        every {
            waiter.matched()
        } returns response(
            DescribeTasksResponse.builder().tasks(
                listOf(
                    Task.builder().taskArn("task1").lastStatus("STOPPED").build(),
                    Task.builder().taskArn("task2").lastStatus("RUNNING").build(),
                    Task.builder().taskArn("task2").lastStatus("STOPPING").build()
                )
            ).build()
        )

        val stopTaskRequestList = mutableListOf<Consumer<StopTaskRequest.Builder>>()
        every {
            ecsClient.stopTask(capture(stopTaskRequestList))
        } returns StopTaskResponse.builder().build()

        val describeTaskRequestList = mutableListOf<Consumer<DescribeTasksRequest.Builder>>()
        val waiterOverrideConfigList = mutableListOf<Consumer<WaiterOverrideConfiguration.Builder>>()
        every {
            ecsClient.waiter().waitUntilTasksStopped(capture(describeTaskRequestList), capture(waiterOverrideConfigList))
        } returns waiter

        val stoppedTaskIDList = ecsTaskManager.stopTasks(inputTaskIDList)
        Assertions.assertTrue(stoppedTaskIDList == listOf("task1"))
    }

    @Test
    fun `when stopTasks called with ecs waiter response contains unexpected task`() {
        val inputTaskIDList = listOf("task1")

        // Here we mock the wait response only contains partial task IDs
        every {
            waiter.matched()
        } returns response(
            DescribeTasksResponse.builder().tasks(
                listOf(
                    Task.builder().taskArn("task1").lastStatus("STOPPED").build(),
                    Task.builder().taskArn("unexpected-task").lastStatus("STOPPED").build()
                )
            ).build()
        )

        val stopTaskRequestList = mutableListOf<Consumer<StopTaskRequest.Builder>>()
        every {
            ecsClient.stopTask(capture(stopTaskRequestList))
        } returns StopTaskResponse.builder().build()

        val describeTaskRequestList = mutableListOf<Consumer<DescribeTasksRequest.Builder>>()
        val waiterOverrideConfigList = mutableListOf<Consumer<WaiterOverrideConfiguration.Builder>>()
        every {
            ecsClient.waiter().waitUntilTasksStopped(capture(describeTaskRequestList), capture(waiterOverrideConfigList))
        } returns waiter

        val stoppedTaskIDList = ecsTaskManager.stopTasks(inputTaskIDList)
        // Although ecs waiter response contains task ID with "unexpected-task", it should not be included to result
        Assertions.assertTrue(stoppedTaskIDList == listOf("task1"))
    }

    @Test
    fun `when stopTasks called with ecsClient waiter raise exception`() {
        val inputTaskIDList = listOf("task1", "task2", "task3", "task4", "task5")

        val stopTaskRequestList = mutableListOf<Consumer<StopTaskRequest.Builder>>()
        every {
            ecsClient.stopTask(capture(stopTaskRequestList))
        } returns StopTaskResponse.builder().build()

        val describeTaskRequestList = mutableListOf<Consumer<DescribeTasksRequest.Builder>>()
        val waiterOverrideConfigList = mutableListOf<Consumer<WaiterOverrideConfiguration.Builder>>()
        every {
            ecsClient.waiter().waitUntilTasksStopped(capture(describeTaskRequestList), capture(waiterOverrideConfigList))
        } throws UnsupportedOperationException()

        assertThrows<EcsTaskManagerException> { ecsTaskManager.stopTasks(inputTaskIDList) }
    }
}
