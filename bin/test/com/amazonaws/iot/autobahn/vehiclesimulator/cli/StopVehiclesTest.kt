@file:Suppress("INLINE_FROM_HIGHER_PLATFORM")
package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import com.amazonaws.iot.autobahn.vehiclesimulator.S3
import com.amazonaws.iot.autobahn.vehiclesimulator.SimulationMetaData
import com.amazonaws.iot.autobahn.vehiclesimulator.SimulatorCliInput
import com.amazonaws.iot.autobahn.vehiclesimulator.VehicleSetupStatus
import com.amazonaws.iot.autobahn.vehiclesimulator.VehicleSimulator
import com.amazonaws.iot.autobahn.vehiclesimulator.VehicleSimulator.Companion.StopStatus
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

internal class StopVehiclesTest {

    private val command = spyk<StopVehicles>(recordPrivateCalls = true)

    private val carList = listOf("car0", "car1")
    private val taskList = listOf("task0", "task1")

    @BeforeEach
    fun setup() {
        mockkConstructor(VehicleSimulator::class)
        command.region = "us-west-2"
        command.cpuArchitecture = "cpu"
        command.ecsTaskIDs = taskList
        command.deleteIoTPolicy = true
        command.deleteIoTCert = true

        every { command["parseSimulationFile"](any<String>()) } returns carList.map {
            SimulatorCliInput(
                SimulationMetaData(it, S3("bucket", it)),
                this.javaClass.classLoader.getResource("$it/config.json")!!.path,
                this.javaClass.classLoader.getResource("$it/sim")!!.path
            )
        }
    }

    @Test
    fun `when call StopVehicles with all things deleted and tasks stopped`() {
        command.simulationInput = "input.json"
        every {
            anyConstructed<VehicleSimulator>().stopVehicles(any())
        } returns StopStatus(taskList.toSet(), setOf())
        coEvery {
            anyConstructed<VehicleSimulator>().clean(any(), any(), any())
        } returns VehicleSetupStatus(carList.toSet(), setOf())
        Assertions.assertEquals(0, command.call())
        verify(exactly = 1) {
            anyConstructed<VehicleSimulator>().stopVehicles(listOf("task0", "task1"))
        }
        coVerify(exactly = 1) {
            anyConstructed<VehicleSimulator>().clean(
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `when call StopVehicles with no simulation input`() {
        every {
            anyConstructed<VehicleSimulator>().stopVehicles(any())
        } returns StopStatus(taskList.toSet(), setOf())
        coEvery {
            anyConstructed<VehicleSimulator>().clean(any(), any(), any())
        } returns VehicleSetupStatus(carList.toSet(), setOf())
        Assertions.assertEquals(0, command.call())
        verify(exactly = 1) {
            anyConstructed<VehicleSimulator>().stopVehicles(listOf("task0", "task1"))
        }
        coVerify(exactly = 0) {
            anyConstructed<VehicleSimulator>().clean(
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `when call StopVehicles with empty simulation input`() {
        command.simulationInput = ""
        every {
            anyConstructed<VehicleSimulator>().stopVehicles(any())
        } returns StopStatus(taskList.toSet(), setOf())
        coEvery {
            anyConstructed<VehicleSimulator>().clean(any(), any(), any())
        } returns VehicleSetupStatus(carList.toSet(), setOf())
        Assertions.assertEquals(0, command.call())
        verify(exactly = 1) {
            anyConstructed<VehicleSimulator>().stopVehicles(listOf("task0", "task1"))
        }
        coVerify(exactly = 0) {
            anyConstructed<VehicleSimulator>().clean(
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `when call StopVehicles with all things deleted and but some tasks failed to stop`() {
        command.simulationInput = "input.json"
        every {
            anyConstructed<VehicleSimulator>().stopVehicles(any())
        } returns StopStatus(setOf("task0"), setOf("task1"))
        coEvery {
            anyConstructed<VehicleSimulator>().clean(any(), any(), any())
        } returns VehicleSetupStatus(carList.toSet(), setOf())
        Assertions.assertEquals(-1, command.call())
        verify(exactly = 1) {
            anyConstructed<VehicleSimulator>().stopVehicles(listOf("task0", "task1"))
        }
        coVerify(exactly = 1) {
            anyConstructed<VehicleSimulator>().clean(
                carList.map { SimulationMetaData(it, S3("bucket", it)) },
                deleteIoTPolicy = true,
                deleteIoTCert = true
            )
        }
    }

    @Test
    fun `when call StopVehicles with some things failed to delete`() {
        command.simulationInput = "input.json"
        every {
            anyConstructed<VehicleSimulator>().stopVehicles(any())
        } returns StopStatus(taskList.toSet(), setOf())
        coEvery {
            anyConstructed<VehicleSimulator>().clean(any(), any(), any())
        } returns VehicleSetupStatus(setOf("car0"), setOf("car1"))
        Assertions.assertEquals(-1, command.call())
        verify(exactly = 1) {
            anyConstructed<VehicleSimulator>().stopVehicles(listOf("task0", "task1"))
        }
        coVerify(exactly = 1) {
            anyConstructed<VehicleSimulator>().clean(
                carList.map { SimulationMetaData(it, S3("bucket", it)) },
                deleteIoTPolicy = true,
                deleteIoTCert = true
            )
        }
    }

    @AfterEach
    fun tearDown() {
        unmockk