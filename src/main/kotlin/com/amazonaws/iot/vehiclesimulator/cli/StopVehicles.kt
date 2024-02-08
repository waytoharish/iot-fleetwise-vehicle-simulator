package com.amazonaws.iot.fleetwise.vehiclesimulator.cli

import com.amazonaws.iot.fleetwise.vehiclesimulator.SimulatorCliInput
import com.amazonaws.iot.fleetwise.vehiclesimulator.VehicleSimulator
import com.amazonaws.iot.fleetwise.vehiclesimulator.storage.S3Storage
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import java.io.File
import java.time.Duration
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "StopVehicles",
    description = ["Stop Virtual Vehicles"],
)
class StopVehicles(private val objectMapper: ObjectMapper = jacksonObjectMapper()) : Callable<Int> {
    @CommandLine.Option(required = true, names = ["--region", "-r"])
    lateinit var region: String

    @CommandLine.Option(names = ["--cpu-architecture", "-a"])
    var cpuArchitecture = "arm64"

    @CommandLine.Option(arity = "0..*", names = ["--ecsTaskID"])
    var ecsTaskIDs = listOf<String>()

    @CommandLine.Option(names = ["--simulation-input", "-s"])
    var simulationInput: String? = null

    @CommandLine.Option(names = ["--delete-iot-policy"])
    var deleteIoTPolicy = false

    @CommandLine.Option(names = ["--delete-iot-certificate"])
    var deleteIoTCert = false

    // Timeout is in unit of minute
    @CommandLine.Option(required = false, names = ["--ecs-waiter-timeout"])
    var ecsWaiterTimeout: Int = 5

    @CommandLine.Option(required = false, names = ["--ecs-waiter-retries"])
    var ecsWaiterRetries: Int = 100

    private val log: Logger = LoggerFactory.getLogger(StopVehicles::class.java)

    private fun parseSimulationFile(simulationPackage: String): List<SimulatorCliInput> {
        return objectMapper.readValue(File(simulationPackage).readText())
    }

    override fun call(): Int {
        var result = 0
        val s3Storage = S3Storage(S3AsyncClient.builder().region(Region.of(region)).build())
        val testManager = VehicleSimulator(region, cpuArchitecture, s3Storage)
        val stopStatus = testManager.stopVehicles(
            ecsTaskIDs,
            timeout = Duration.ofMinutes(ecsWaiterTimeout.toLong()),
            retries = ecsWaiterRetries
        )
        log.info("Successfully stopped ecs tasks: ${stopStatus.successList}")
        if (stopStatus.failedList.isNotEmpty()) {
            log.error("Failed to stop ecs tasks: ${stopStatus.failedList}")
            result = -1
        }
        if (!simulationInput.isNullOrEmpty()) {
            val simulationMetaData = parseSimulationFile(simulationInput!!).map {
                it.simulationMetaData
            }
            val thingDeletionStatus = runBlocking(Dispatchers.IO) {
                testManager.clean(
                    simulationMetaData,
                    deleteIoTPolicy = deleteIoTPolicy,
                    deleteIoTCert = deleteIoTCert
                )
            }
            log.info("Successfully deleted things: ${thingDeletionStatus.successList}")
            if (thingDeletionStatus.failedList.isNotEmpty()) {
                log.error("Failed to delete things: ${thingDeletionStatus.failedList}")
                result = -1
            }
            log.info("Finish clean up")
        }
        return result
    }
}
