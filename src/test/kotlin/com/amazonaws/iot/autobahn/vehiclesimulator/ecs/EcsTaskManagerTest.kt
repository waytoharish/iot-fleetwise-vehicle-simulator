package com.amazonaws.iot.autobahn.vehiclesimulator.ecs

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse
import software.amazon.awssdk.services.ecs.model.RunTaskRequest
import software.amazon.awssdk.services.ecs.model.RunTaskResponse
import software.amazon.awssdk.services.ecs.model.StopTaskRequest
import software.amazon.awssdk.services.ecs.model.StopTaskResponse
import software.amazon.awssdk.services.ecs.model.Task
import java.util.function.Consumer

internal class EcsTaskManagerTest {

    private val ecsClient = mockk<EcsClient>()

    private val ecsTaskManager = spyk(EcsTaskManager(ecsClient), recordPrivateCalls = true)

    private val simulationPackageUrl = "test-url"

    // Happy Path Testing. All ECS tasks are running
    @Test
    fun `when runTasks successfully invoked ECS to run two tasks`() {
        val newTasks = mutableListOf(
            Task.builder().taskArn("test-task-1").lastStatus("RUNNING").build(),
            Task.builder().taskArn("test-task-2").lastStatus("RUNNING").build()
        )

        every { ecsTaskManager["calculateComputingResource"](4) } returns Triple(2, 512, 512)

        every {
            ecsClient.runTask(any<Consumer<RunTaskRequest.Builder>>())
        } returns RunTaskResponse.builder().tasks(newTasks).build()

        every {
            ecsClient.describeTasks(any<Consumer<DescribeTasksRequest.Builder>>())
        } returns DescribeTasksResponse.builder().tasks(newTasks).build()

        val taskArnList = ecsTaskManager.runTasks(simulationPackageUrl)
        Assertions.assertEquals(2, taskArnList.size)
        Assertions.assertTrue(arrayOf("test-task-1", "test-task-2") contentEquals taskArnList)
    }

    // This test aims to validate runTasks can handle failure case when the tasks last status
    // shown as STOPPED.
    @Test
    fun `when runTasks called with ECS task last status shown as STOPPED`() {
        val newTasks = mutableListOf(
            Task.builder().taskArn("test-task-1").lastStatus("STOPPED").build(),
            Task.builder().taskArn("test-task-2").lastStatus("STOPPED").build()
        )

        every { ecsTaskManager["calculateComputingResource"](4) } returns Triple(2, 512, 512)

        every {
            ecsClient.runTask(any<Consumer<RunTaskRequest.Builder>>())
        } returns RunTaskResponse.builder().tasks(newTasks).build()

        every {
            ecsClient.describeTasks(any<Consumer<DescribeTasksRequest.Builder>>())
        } returns DescribeTasksResponse.builder().tasks(newTasks).build()

        val taskArnList = ecsTaskManager.runTasks(simulationPackageUrl)
        Assertions.assertEquals(0, taskArnList.size)
    }

    // This test aims to validate runTasks can handle failure case when the task list from
    // RunTaskResponse mismatch with expected task list
    @Test
    fun `when runTasks called with ecsClient runTask returned invalid response`() {
        val newTasks = mutableListOf(
            Task.builder().taskArn("test-task-1").lastStatus("STOPPED").build()
        )

        every { ecsTaskManager["calculateComputingResource"](4) } returns Triple(2, 512, 512)

        every {
            ecsClient.runTask(any<Consumer<RunTaskRequest.Builder>>())
        } returns RunTaskResponse.builder().tasks(newTasks).build()

        val taskArnList = ecsTaskManager.runTasks(simulationPackageUrl)
        // The runTaskResponse is invalid as it only contains one task while we are expecting 2 tasks.
        Assertions.assertEquals(0, taskArnList.size)
    }

    // This test aims to validate runTasks can handle failure case when the task list from
    // DescribeTaskResponse contains empty task list
    @Test
    fun `when runTasks called with ecsClient describeTask returned empty task lists`() {
        val newTasks = mutableListOf(
            Task.builder().taskArn("test-task-1").lastStatus("RUNNING").build(),
            Task.builder().taskArn("test-task-2").lastStatus("RUNNING").build()
        )

        every { ecsTaskManager["calculateComputingResource"](4) } returns Triple(2, 512, 512)

        every {
            ecsClient.runTask(any<Consumer<RunTaskRequest.Builder>>())
        } returns RunTaskResponse.builder().tasks(newTasks).build()

        // Here we mock describeTasks respond with empty task list
        every {
            ecsClient.describeTasks(any<Consumer<DescribeTasksRequest.Builder>>())
        } returns DescribeTasksResponse.builder().tasks(mutableListOf()).build()

        val taskArnList = ecsTaskManager.runTasks(simulationPackageUrl)
        // The runTaskResponse is invalid as it only contains one task while we are expecting 2 tasks.
        Assertions.assertEquals(0, taskArnList.size)
    }

    // This test aims to validate runTasks can handle failure case when only partial tasks are started
    @Test
    fun `when runTasks called with only partial ecsClient task running`() {
        val newTasks = mutableListOf(
            Task.builder().taskArn("test-task-1").lastStatus("RUNNING").build(),
            Task.builder().taskArn("test-task-2").lastStatus("RUNNING").build()
        )

        every { ecsTaskManager["calculateComputingResource"](4) } returns Triple(2, 512, 512)

        every {
            ecsClient.runTask(any<Consumer<RunTaskRequest.Builder>>())
        } returns RunTaskResponse.builder().tasks(newTasks).build()

        // Here we mock describeTasks respond with one task running and one task stopped
        every {
            ecsClient.describeTasks(any<Consumer<DescribeTasksRequest.Builder>>())
        } returns DescribeTasksResponse.builder().tasks(
            mutableListOf(
                Task.builder().taskArn("test-task-1").lastStatus("RUNNING").build(),
                Task.builder().taskArn("test-task-2").lastStatus("STOPPED").build()
            )
        ).build()

        val taskArnList = ecsTaskManager.runTasks(simulationPackageUrl)
        // The runTaskResponse is invalid as it only contains one task while we are expecting 2 tasks.
        Assertions.assertEquals(1, taskArnList.size)
        Assertions.assertTrue(taskArnList contentEquals arrayOf("test-task-1"))
    }

    // Happy Path Testing. All ECS tasks are STOPPED
    @Test
    fun `when stopTasks called with ECS tasks successfully STOPPED`() {
        val taskIDList = arrayOf("task1", "task2", "task3", "task4", "task5")

        val taskList = mutableListOf(
            Task.builder().taskArn("testCluster/task1").lastStatus("STOPPED").build(),
            Task.builder().taskArn("testCluster/task2").lastStatus("STOPPED").build(),
            Task.builder().taskArn("testCluster/task3").lastStatus("STOPPED").build(),
            Task.builder().taskArn("testCluster/task4").lastStatus("STOPPED").build(),
            Task.builder().taskArn("testCluster/task5").lastStatus("STOPPED").build()
        )

        every {
            ecsClient.stopTask(any<Consumer<StopTaskRequest.Builder>>())
        } returns StopTaskResponse.builder().build()

        every {
            ecsClient.describeTasks(any<Consumer<DescribeTasksRequest.Builder>>())
        } returns DescribeTasksResponse.builder().tasks(taskList).build()

        val result = ecsTaskManager.stopTasks(taskIDList)
        Assertions.assertEquals(0, result)
    }

    // In this test, task2 doesn't exist in ECS. The test aims to validate the program
    // can gracefully handle this scenario and still able to stop task1
    @Test
    fun `when stopTasks called with non existed task ID`() {
        val taskIDList = arrayOf("task1", "task2")

        val taskList = mutableListOf(
            Task.builder().taskArn("testCluster/task1").lastStatus("STOPPED").build()
        )

        every {
            ecsClient.stopTask(any<Consumer<StopTaskRequest.Builder>>())
        } returns StopTaskResponse.builder().build()

        every {
            ecsClient.describeTasks(any<Consumer<DescribeTasksRequest.Builder>>())
        } returns DescribeTasksResponse.builder().tasks(taskList).build()

        val result = ecsTaskManager.stopTasks(taskIDList)
        Assertions.assertEquals(0, result)
    }
}
