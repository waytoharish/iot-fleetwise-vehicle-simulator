package com.amazonaws.iot.autobahn.vehiclesimulator.ecs

import com.amazonaws.iot.autobahn.vehiclesimulator.exceptions.EcsTaskManagerException
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.core.exception.SdkClientException
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
import kotlin.math.max

internal class EcsTaskManagerTest {

    private val ecsClient = mockk<EcsClient>()

    private val ecsTaskManager = spyk(EcsTaskManager(ecsClient), recordPrivateCalls = true)

    private val waiter = mockk<WaiterResponse<DescribeTasksResponse>>()

    private val oneVehicleSimulation = mapOf(
        "car1" to "sim-url-for-car1"
    )

    private val smallFleetSimulation = mapOf(
        "car1" to "sim-url-for-car1",
        "car2" to "sim-url-for-car2",
        "car3" to "sim-url-for-car3",
        "car4" to "sim-url-for-car4"
    )

    // Large Fleet Testing intend to exercise ecsClient API call at least 3 loops
    private val maxNumOfTasksPerBatch = max(
        EcsTaskManager.MAX_TASK_PER_RUN_TASK_REQUEST,
        EcsTaskManager.MAX_TASK_PER_WAIT_TASK
    )
    private val largeFleetSize = maxNumOfTasksPerBatch * 2 + 1

    // Below create a large fleet vehicle input with MAX_NUM_OF_VEHICLES_PER_TASK + 1 of vehicles
    private val largeFleetSimulation: Map<String, String> = (1..largeFleetSize)
        .map { "car$it".toString() }
        .zip(
            (1..largeFleetSize)
                .map { "s3://car$it".toString() }
        ).toMap()

    @Test
    fun `when runTasks with invoking ecsClient to run one tasks`() {
        val expectedTaskArnList = listOf("test-task-1")
        val newTasks = expectedTaskArnList.map {
            Task.builder().taskArn(it).lastStatus("RUNNING").build()
        }

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

        val returnedTaskArnList = ecsTaskManager.runTasks(oneVehicleSimulation)

        val requestedTaskCount = runTaskRequestList.map {
            val builder = RunTaskRequest.builder()
            it.accept(builder)
            builder.build().count()
        }[0]
        Assertions.assertEquals(1, requestedTaskCount)

        val requestedVehicleIDList = runTaskRequestList.map {
            val builder = RunTaskRequest.builder()
            it.accept(builder)
            builder.build().overrides().containerOverrides()[0].environment()[0]
        }[0]
        Assertions.assertEquals("VEHICLE_ID", requestedVehicleIDList.name())
        Assertions.assertEquals("car1", requestedVehicleIDList.value())

        val requestedSimulationUrlList = runTaskRequestList.map {
            val builder = RunTaskRequest.builder()
            it.accept(builder)
            builder.build().overrides().containerOverrides()[0].environment()[1]
        }[0]
        Assertions.assertEquals("SIM_PKG_URL", requestedSimulationUrlList.name())
        Assertions.assertEquals("sim-url-for-car1", requestedSimulationUrlList.value())

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
    fun `when runTasks called with ecsClient runTaskResponse contain incorrect number of tasks`() {
        val newTasks = listOf<String>().map {
            Task.builder().taskArn(it).lastStatus("RUNNING").build()
        }

        val runTaskRequestList = mutableListOf<Consumer<RunTaskRequest.Builder>>()
        every {
            ecsClient.runTask(capture(runTaskRequestList))
        } returns RunTaskResponse.builder().tasks(newTasks).build()

        // The RunTaskResponse returns 0 task instead of 1. We shall expect an exception being thrown
        assertThrows<EcsTaskManagerException> { ecsTaskManager.runTasks(oneVehicleSimulation) }

        val requestedTaskCount = runTaskRequestList.map {
            val builder = RunTaskRequest.builder()
            it.accept(builder)
            builder.build().count()
        }[0]
        Assertions.assertEquals(1, requestedTaskCount)
    }

    @Test
    fun `when runTasks called with ecsClient waiter raise exceptions`() {
        val expectedTaskArnList = listOf("task1")
        val newTasks = expectedTaskArnList.map {
            Task.builder().taskArn(it).lastStatus("PENDING").build()
        }

        val runTaskRequestList = mutableListOf<Consumer<RunTaskRequest.Builder>>()
        every {
            ecsClient.runTask(capture(runTaskRequestList))
        } returns RunTaskResponse.builder().tasks(newTasks).build()

        val describeTaskRequestList = mutableListOf<Consumer<DescribeTasksRequest.Builder>>()
        val waiterOverrideConfigList = mutableListOf<Consumer<WaiterOverrideConfiguration.Builder>>()
        listOf<Exception>(
            UnsupportedOperationException(),
            SdkClientException.builder().build()
        ).map {
            every {
                ecsClient.waiter().waitUntilTasksRunning(capture(describeTaskRequestList), capture(waiterOverrideConfigList))
            } throws it
            assertThrows<EcsTaskManagerException> {
                ecsTaskManager.runTasks(smallFleetSimulation)
            }
        }
    }

    // This test aims to validate runTasks can handle failure case when the task list from
    // RunTaskResponse mismatch with expected task list
    @Test
    fun `when runTasks called with only partial tasks running after wait timeout`() {
        // mock Task2 never turn RUNNING
        every {
            waiter.attemptsExecuted()
        } returns 42
        every {
            waiter.matched()
        } returns response(
            DescribeTasksResponse.builder().tasks(
                (1..maxNumOfTasksPerBatch).map {
                    Task.builder().taskArn("task$it").lastStatus("RUNNING").build()
                }
            ).build()
        ) andThen response(
            DescribeTasksResponse.builder().tasks(
                (maxNumOfTasksPerBatch + 1..maxNumOfTasksPerBatch * 2).map {
                    Task.builder().taskArn("task$it").lastStatus("RUNNING").build()
                }
            ).build()
        ) andThen response(
            DescribeTasksResponse.builder().tasks(
                listOf(
                    // Here we mock the last task still shown as PENDING when waiter return
                    Task.builder().taskArn("task$largeFleetSize").lastStatus("PENDING").build()
                )
            ).build()
        )

        val runTaskRequestList = mutableListOf<Consumer<RunTaskRequest.Builder>>()
        every {
            ecsClient.runTask(capture(runTaskRequestList))
        } returns RunTaskResponse
            .builder()
            .tasks(
                listOf(
                    Task.builder().taskArn("task1").lastStatus("PENDING").build()
                )
            ).build() andThenMany
            (2..largeFleetSize).map {
                RunTaskResponse.builder().tasks(
                    Task.builder().taskArn("task$it").lastStatus("PENDING").build()
                ).build()
            }
        val describeTaskRequestList = mutableListOf<Consumer<DescribeTasksRequest.Builder>>()
        val waiterOverrideConfigList = mutableListOf<Consumer<WaiterOverrideConfiguration.Builder>>()
        every {
            ecsClient.waiter().waitUntilTasksRunning(capture(describeTaskRequestList), capture(waiterOverrideConfigList))
        } returns waiter

        val returnedTaskArnList = ecsTaskManager.runTasks(largeFleetSimulation)
        Assertions.assertTrue((1 until largeFleetSize).map { "task$it" } == returnedTaskArnList)

        val requestedTaskCount = runTaskRequestList.map {
            val builder = RunTaskRequest.builder()
            it.accept(builder)
            builder.build().count()
        }[0]
        Assertions.assertEquals(1, requestedTaskCount)

        val describeTaskRequestTaskList = describeTaskRequestList.map {
            val builder = DescribeTasksRequest.builder()
            it.accept(builder)
            builder.build().tasks()
        }
        Assertions.assertEquals((1..maxNumOfTasksPerBatch).map { "task$it" }, describeTaskRequestTaskList[0])
        Assertions.assertEquals((maxNumOfTasksPerBatch + 1..maxNumOfTasksPerBatch * 2).map { "task$it" }, describeTaskRequestTaskList[1])
        Assertions.assertEquals((maxNumOfTasksPerBatch * 2 + 1 until maxNumOfTasksPerBatch * 2 + 2).map { "task$it" }, describeTaskRequestTaskList[2])

        val actualWaiterOverrideConfig = waiterOverrideConfigList.map {
            val builder = WaiterOverrideConfiguration.builder()
            it.accept(builder)
            builder.build().waitTimeout()
        }[0]
        Assertions.assertEquals(5, actualWaiterOverrideConfig.get().toMinutes())
    }

    @Test
    fun `when stopTasks called with ECS tasks successfully STOPPED`() {
        val inputTaskIDList = (1..largeFleetSize).map { "task$it" }

        every {
            waiter.matched()
        } returns response(
            DescribeTasksResponse.builder().tasks(
                (1..maxNumOfTasksPerBatch).map {
                    Task.builder().taskArn("task$it").lastStatus("STOPPED").build()
                }
            ).build()
        ) andThen response(
            DescribeTasksResponse.builder().tasks(
                (maxNumOfTasksPerBatch + 1..maxNumOfTasksPerBatch * 2).map {
                    Task.builder().taskArn("task$it").lastStatus("STOPPED").build()
                }
            ).build()
        ) andThen response(
            DescribeTasksResponse.builder().tasks(
                listOf(
                    // Here we mock the last task still shown as PENDING when waiter return
                    Task.builder().taskArn("task$largeFleetSize").lastStatus("STOPPED").build()
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
        Assertions.assertEquals((1..maxNumOfTasksPerBatch).map { "task$it" }, describeTaskRequestTaskList[0])
        Assertions.assertEquals((maxNumOfTasksPerBatch + 1..maxNumOfTasksPerBatch * 2).map { "task$it" }, describeTaskRequestTaskList[1])
        Assertions.assertEquals((maxNumOfTasksPerBatch * 2 + 1 until maxNumOfTasksPerBatch * 2 + 2).map { "task$it" }, describeTaskRequestTaskList[2])

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
        listOf<Exception>(
            UnsupportedOperationException(),
            SdkClientException.builder().build()
        ).map {
            every {
                ecsClient.waiter().waitUntilTasksStopped(capture(describeTaskRequestList), capture(waiterOverrideConfigList))
            } throws it
            assertThrows<EcsTaskManagerException> { ecsTaskManager.stopTasks(inputTaskIDList) }
        }
    }
}
