package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import com.amazonaws.iot.autobahn.vehiclesimulator.S3
import com.amazonaws.iot.autobahn.vehiclesimulator.SimulationMetaData
import com.amazonaws.iot.autobahn.vehiclesimulator.SimulatorCliInput
import com.amazonaws.iot.autobahn.vehiclesimulator.VehicleSetupStatus
import com.amazonaws.iot.autobahn.vehiclesimulator.VehicleSimulator
import com.amazonaws.iot.autobahn.vehiclesimulator.VehicleSimulator.Companion.LaunchStatus
import com.amazonaws.iot.autobahn.vehiclesimulator.iot.IoTThingManager.Companion.DEFAULT_POLICY_NAME
import com.amazonaws.iot.autobahn.vehiclesimulator.iot.IoTThingManager.Companion.DEFAULT_RICH_DATA_POLICY_DOCUMENT
import com.amazonaws.iot.autobahn.vehiclesimulator.storage.S3Storage
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.spyk
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

internal class LaunchVehiclesTest {
    private val objectMapper = jacksonObjectMapper()
    private val command = spyk(LaunchVehicles(objectMapper), recordPrivateCalls = true)

    private val carList = listOf("car0", "car1")
    private val simInput = carList.map {
        SimulationMetaData(it, S3("test-bucket", it))
    }

    @BeforeEach
    fun setup() {
        mockkConstructor(VehicleSimulator::class)
        mockkConstructor(S3Storage::class)
        // Mock for S3 Storage
        coEvery {
            anyConstructed<S3Storage>().put(any(), any(), any())
        } returns Unit

        command.simulationInput = "mock_input.json"
        command.region = "test-region"
        command.ecsTaskDefinition = "test-task-definition"
        command.tags = listOf("user", "abc", "time", "xyz")
        command.stage = "gamma"
        command.cpuArchitecture = "arm64"
        command.recreateIoTPolicyIfExists = true
        // We mock parseSimulationFile to return the simulation input
        every { command["parseSimulationFile"](any<String>()) } returns listOf("car0", "car1").map {
            SimulatorCliInput(
                SimulationMetaData(it, S3("test-bucket", it)),
                this.javaClass.classLoader.getResource("$it/config.json")!!.path,
                this.javaClass.classLoader.getResource("$it/sim")!!.path
            )
        }
    }

    @Test
    fun `when call LaunchVehicles with all things created and tasks running`() {
        val configMap = carList.associateWith {
            this.javaClass.classLoader.getResourceAsStream("$it/config.json")!!.bufferedReader().readText()
        }
        coEvery {
            anyConstructed<VehicleSimulator>().preLaunch(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns VehicleSetupStatus(carList.toSet(), setOf())
        every {
            anyConstructed<VehicleSimulator>().launchVehicles(any(), any(), any(), any(), any(), any())
        } returns carList.map { LaunchStatus(it, "task") }

        Assertions.assertEquals(0, command.call())

        coVerify {
            anyConstructed<VehicleSimulator>().preLaunch(
                objectMapper,
                simInput,
                configMap,
                "gamma",
                DEFAULT_POLICY_NAME,
                DEFAULT_RICH_DATA_POLICY_DOCUMENT,
                true
            )
        }

        verify {
            anyConstructed<VehicleSimulator>().launchVehicles(
                simInput,
                "test-task-definition",
                ecsCapacityProviderName = "ubuntu-arm64-capacity-provider",
                tags = mapOf("user" to "abc", "time" to "xyz"),
                timeout = Duration.ofMinutes(5),
                retries = 100
            )
        }
    }

    @Test
    fun `when call LaunchVehicles with things failed to create`() {
        coEvery {
            anyConstructed<VehicleSimulator>().preLaunch(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns VehicleSetupStatus(setOf("car0"), setOf("car1"))
        every {
            anyConstructed<VehicleSimulator>().launchVehicles(any(), any(), any(), any(), any(), any())
        } returns carList.map { LaunchStatus(it, "task") }

        Assertions.assertEquals(-1, command.call())
    }

    @Test
    fun `when call LaunchVehicles with all things created and but some ecs tasks not running`() {
        coEvery {
            anyConstructed<VehicleSimulator>().preLaunch(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns VehicleSetupStatus(carList.toSet(), setOf())
        every {
            anyConstructed<VehicleSimulator>().launchVehicles(any(), any(), any(), any(), any(), any())
        } returns listOf(LaunchStatus("car1", "task1"))

        Assertions.assertEquals(-1, command.call())
    }

    @AfterEach
    fun tearDown() {
        unmockkConstructor(VehicleSimulator::class)
        unmockkConstructor(S3Storage::class)
    }
}
