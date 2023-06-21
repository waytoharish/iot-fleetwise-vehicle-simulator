package com.amazonaws.iot.autobahn.vehiclesimulator

import com.amazonaws.iot.autobahn.vehiclesimulator.VehicleSimulator.Companion.LaunchStatus
import com.amazonaws.iot.autobahn.vehiclesimulator.cert.ACMCertificateManager
import com.amazonaws.iot.autobahn.vehiclesimulator.ecs.EcsTaskManager
import com.amazonaws.iot.autobahn.vehiclesimulator.edgeConfig.EdgeConfigProcessor
import com.amazonaws.iot.autobahn.vehiclesimulator.iot.IoTThingManager
import com.amazonaws.iot.autobahn.vehiclesimulator.storage.S3Storage
import com.amazonaws.iot.autobahn.vehiclesimulator.utils.TestUtils
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

class VehicleSimulatorTest {
    private val s3Storage = mockk<S3Storage>()
    private val ioTThingManager = mockk<IoTThingManager>()
    private val ecsTaskManager = mockk<EcsTaskManager>()
    private val acmCertificateManager = mockk<ACMCertificateManager>()
    private val vehicleSimulator = VehicleSimulator("us-east-1", "cpu", s3Storage, ioTThingManager, acmCertificateManager, ecsTaskManager)

    private val simInput = listOf("car0", "car1").map {
        SimulationMetaData(it, S3("test-bucket", it))
    }

    private val objectMapper = jacksonObjectMapper()

    private val carList = listOf("car0", "car1")

    @BeforeEach
    fun setup() {
        coEvery {
            s3Storage.put(any(), any(), any())
        } returns Unit

        coEvery {
            s3Storage.deleteObjects(any(), any())
        } returns Unit

        coEvery {
            ioTThingManager.getIoTCoreDataEndPoint()
        } returns "iot-core-end-point"
    }

    @Test
    fun `launch vehicles with ecs`() {
        every {
            ecsTaskManager.runTasks(any(), any(), any(), any(), any(), any(), any(), any())
        } returns mapOf(
            "car0" to "task0",
            "car1" to "task1"
        )

        val result = vehicleSimulator.launchVehicles(
            listOf("car0", "car1").map {
                SimulationMetaData(it, S3("test-bucket", it))
            },
            ecsTaskDefinition = "test-task-def",
            ecsCapacityProviderName = "test-cap-provider",
            tags = mapOf("tag1" to "value1", "tag2" to "value2"),
            timeout = Duration.ofMinutes(5),
            retries = 100
        )
        verify(exactly = 1) {
            ecsTaskManager.runTasks(
                listOf("car0", "car1").map {
                    SimulationMetaData(it, S3("test-bucket", it))
                },
                ecsTaskDefinition = "test-task-def",
                useCapacityProvider = true,
                ecsLaunchType = "EC2",
                ecsCapacityProviderName = "test-cap-provider",
                tags = mapOf("tag1" to "value1", "tag2" to "value2"),
                waiterTimeout = Duration.ofMinutes(5),
                waiterRetries = 100
            )
        }
        Assertions.assertTrue(
            listOf(
                LaunchStatus("car0", "task0"),
                LaunchStatus("car1", "task1")
            ) == result
        )
    }

    @Test
    fun `When preLaunch is called with all IoT Things created and S3 upload successful`() {
        coEvery {
            ioTThingManager.createAndStoreThings(any(), any(), any(), any())
        } returns VehicleSetupStatus(carList.toSet(), setOf())
        mockkConstructor(EdgeConfigProcessor::class)
        every {
            anyConstructed<EdgeConfigProcessor>().setMqttConnectionParameter(any(), any())
        } returns carList.associateWith { "newConfigFor$it" }
        val thingCreationStatus = runBlocking {
            vehicleSimulator.preLaunch(
                objectMapper,
                simInput,
                mapOf(),
                "gamma",
                "test-policy",
                "test-policy-document",
                true
            )
        }
        carList.associateWith { "newConfigFor$it" }.forEach {
            coVerify(exactly = 1) {
                s3Storage.put(
                    "test-bucket",
                    "${it.key}/config.json",
                    it.value.toByteArray()
                )
            }
        }
        Assertions.assertTrue(carList.toSet() == thingCreationStatus.successList)
        Assertions.assertTrue(thingCreationStatus.failedList.isEmpty())
        unmockkConstructor(EdgeConfigProcessor::class)
    }

    @Test
    fun `when prelaunch is called with all simulation input setting createThing to false, device certificate is created using private ACMPCA and things are not provisioned`() {
        val numVehicles = 10
        val simulationInput = TestUtils.createSimulationInput(numVehicles, false)
        val carSet = simulationInput.map { it.vehicleId }.toSet()

        coEvery {
            acmCertificateManager.setupVehiclesWithCertAndPrivateKey(simulationInput)
        } returns VehicleSetupStatus(carSet, emptySet())

        mockkConstructor(EdgeConfigProcessor::class)
        every {
            anyConstructed<EdgeConfigProcessor>().setMqttConnectionParameter(any(), any())
        } returns carSet.associateWith { "newConfigFor$it" }

        val thingCreationStatus = runBlocking {
            vehicleSimulator.preLaunch(
                objectMapper,
                simulationInput,
                mapOf(),
                "gamma",
                "test-policy",
                "test-policy-document",
                true
            )
        }

        coVerify(exactly = 0) { ioTThingManager.createAndStoreThings(any(), any(), any(), any()) }

        coVerify(exactly = 1) { acmCertificateManager.setupVehiclesWithCertAndPrivateKey(simulationInput) }

        simulationInput.associateWith { "newConfigFor${it.vehicleId}" }.forEach {
            coVerify(exactly = 1) {
                s3Storage.put(
                    it.key.s3.bucket,
                    "${it.key.s3.key}/${VehicleSimulator.CONFIG_FILE_NAME}",
                    it.value.toByteArray()
                )
            }
        }

        Assertions.assertTrue(carSet == thingCreationStatus.successList)
        Assertions.assertTrue(thingCreationStatus.failedList.isEmpty())
        unmockkConstructor(EdgeConfigProcessor::class)
    }

    @Test
    fun `when prelaunch is called with some simulation input setting createThing to false, those vehicles are not provisioned, rest are provisioned`() {
        val numVehicles = 10

        val simulationInputNoProvision = TestUtils.createSimulationInput(numVehicles, false)
        val simulationInputProvision = TestUtils.createSimulationInput(numVehicles, true, "car")
        val simulationInput = simulationInputNoProvision + simulationInputProvision
        val carSetProvision = simulationInputProvision.map { it.vehicleId }.toSet()
        val carSetNoProvision = simulationInputNoProvision.map { it.vehicleId }.toSet()
        val carSet = carSetNoProvision + carSetProvision

        coEvery {
            ioTThingManager.createAndStoreThings(any(), any(), any(), any())
        } returns VehicleSetupStatus(carSetProvision, setOf())

        coEvery {
            acmCertificateManager.setupVehiclesWithCertAndPrivateKey(simulationInputNoProvision)
        } returns VehicleSetupStatus(carSetNoProvision, emptySet())

        mockkConstructor(EdgeConfigProcessor::class)
        every {
            anyConstructed<EdgeConfigProcessor>().setMqttConnectionParameter(any(), any())
        } returns carSet.associateWith { "newConfigFor$it" }

        val thingCreationStatus = runBlocking {
            vehicleSimulator.preLaunch(
                objectMapper,
                simulationInput,
                mapOf(),
                "gamma",
                "test-policy",
                "test-policy-document",
                true
            )
        }

        coVerify(exactly = 1) { ioTThingManager.createAndStoreThings(simulationInputProvision, any(), any(), any()) }

        coVerify(exactly = 1) { acmCertificateManager.setupVehiclesWithCertAndPrivateKey(simulationInputNoProvision) }

        simulationInput.associateWith { "newConfigFor${it.vehicleId}" }.forEach {
            coVerify(exactly = 1) {
                s3Storage.put(
                    it.key.s3.bucket,
                    "${it.key.s3.key}/${VehicleSimulator.CONFIG_FILE_NAME}",
                    it.value.toByteArray()
                )
            }
        }

        Assertions.assertTrue(carSet == thingCreationStatus.successList)
        Assertions.assertTrue(thingCreationStatus.failedList.isEmpty())
        unmockkConstructor(EdgeConfigProcessor::class)
    }

    @Test
    fun stopVehicles() {
        every { ecsTaskManager.stopTasks(any(), any(), any()) } returns listOf("task1", "task2")
        val result = vehicleSimulator.stopVehicles(listOf("task1", "task2"))
        Assertions.assertTrue(setOf("task1", "task2") == result.successList)
        Assertions.assertTrue(result.failedList.isEmpty())
    }

    @Test
    fun postTermination() {
        coEvery {
            s3Storage.listObjects(any(), any())
        } returns listOf("car0/item1") andThen listOf("car1/item2")
        coEvery {
            ioTThingManager.deleteThings(any(), any(), any(), any())
        } returns VehicleSetupStatus(carList.toSet(), setOf())
        val thingDeletionStatus = runBlocking {
            vehicleSimulator.clean(simInput, "test-policy", deleteIoTPolicy = true, deleteIoTCert = true)
        }
        coVerify(exactly = 1) {
            ioTThingManager.deleteThings(simInput, "test-policy", deletePolicy = true, deleteCert = true)
        }
        coVerify(exactly = 1) {
            s3Storage.deleteObjects("test-bucket", listOf("car0/item1", "car1/item2"))
        }
        Assertions.assertTrue(carList.toSet() == thingDeletionStatus.successList)
        Assertions.assertTrue(thingDeletionStatus.failedList.isEmpty())
    }
}
