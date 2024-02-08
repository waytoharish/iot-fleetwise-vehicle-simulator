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
import picocli.CommandLine.Command
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.Callable
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.relativeTo
import kotlin.streams.toList

@Command(
    name = "LaunchVehicles",
    description = ["Launch Virtual Vehicles and start simulation"],
)

class LaunchVehicles(private val objectMapper: ObjectMapper = jacksonObjectMapper()) : Callable<Int> {
    @CommandLine.Option(required = true, names = ["--simulation-input", "-s"])
    lateinit var simulationInput: String

    @CommandLine.Option(required = true, names = ["--region", "-r"])
    lateinit var region: String

    @CommandLine.Option(required = true, names = ["--decoder-manifest-arn", "-d"])
    lateinit var decoderManifestArn: String 

    @CommandLine.Option(required = true, names = ["--vehicle-model-arn", "-m"])
    lateinit var vehicleModelArn: String 

    @CommandLine.Option(required = false, names = ["--createVehicles", "-c"])
    var createVehicles: Boolean = false

    @CommandLine.Option(required = false, arity = "0..*", names = ["--tag", "-t"])
    var tags: List<String> = listOf()

    @CommandLine.Option(required = false, names = ["--cpu-architecture", "-a"])
    var cpuArchitecture: String = "amd64"

    @CommandLine.Option(required = false, names = ["--recreate-iot-policy"])
    var recreateIoTPolicyIfExists: Boolean = false

    @CommandLine.Option(required = false, names = ["--ecs-task-definition"])
    var ecsTaskDefinition: String = "fwe-$cpuArchitecture-with-cw"

    // Timeout is in unit of minute
    @CommandLine.Option(required = false, names = ["--ecs-waiter-timeout", "-w"])
    var ecsWaiterTimeout: Int = 5

    @CommandLine.Option(required = false, names = ["--ecs-waiter-retries"])
    var ecsWaiterRetries: Int = 100

    private val log: Logger = LoggerFactory.getLogger(LaunchVehicles::class.java)

    private fun parseSimulationFile(simulationPackage: String): List<SimulatorCliInput> {
        return objectMapper.readValue(File(simulationPackage).readText())
    }

    @OptIn(ExperimentalPathApi::class)
    private suspend fun uploadLocalSimFiles(simConfigMaps: List<SimulatorCliInput>, s3Storage: S3Storage) {
        simConfigMaps.filter { it.localPath.isNotEmpty() }.forEach { simConfig ->
            Files.walk(Paths.get(simConfig.localPath))
                .filter { Files.isRegularFile(it) }
                .toList()
                .map {
                    val fileStream = File(it.toAbsolutePath().toString()).readBytes()
                    s3Storage.put(simConfig.simulationMetaData.s3.bucket, "${simConfig.simulationMetaData.s3.key}/sim/${it.relativeTo(Paths.get(simConfig.localPath))}", fileStream)
                }
        }
    }

    private fun readConfigFile(simConfigMaps: List<SimulatorCliInput>): Map<String, String> {
        return simConfigMaps.associate {
            Pair(it.simulationMetaData.vehicleId, File(it.configPath).readText())
        }
    }

    override fun call(): Int {
        log.info("Simulation Map file $simulationInput and region $region and platform $cpuArchitecture")
        val simConfigMaps = parseSimulationFile(simulationInput)
        val edgeConfigFiles = readConfigFile(simConfigMaps)
        val s3Storage = S3Storage(S3AsyncClient.builder().region(Region.of(region)).build())
        log.info("Uploading simulation files to S3")
        runBlocking(Dispatchers.IO) { uploadLocalSimFiles(simConfigMaps, s3Storage) }
        val vehicleSimulator = VehicleSimulator(region, cpuArchitecture, s3Storage)
        val thingCreationStatus = runBlocking(Dispatchers.IO) {
            vehicleSimulator.preLaunch(
                objectMapper,
                simConfigMaps.map { it.simulationMetaData },
                edgeConfigFiles,
                recreateIoTPolicyIfExists = recreateIoTPolicyIfExists,
                decoderManifest = decoderManifestArn,
                vehicleModel = vehicleModelArn,
                createVehicles = createVehicles
            )
        }
        log.info("Set up vehicles: ${thingCreationStatus.successList}")
        if (thingCreationStatus.failedList.isNotEmpty()) {
            log.error("Failed to setup vehicles: ${thingCreationStatus.failedList}")
            log.error("Cannot continue simulation as not all things are created successful")
            return -1
        }
        val launchStatus = vehicleSimulator.launchVehicles(
            simConfigMaps.map { it.simulationMetaData },
            ecsTaskDefinition = ecsTaskDefinition,
            tags = tags.filterIndexed { index, _ -> index % 2 == 0 }.zip(
                tags.filterIndexed { index, _ -> index % 2 == 1 }
            ).toMap(),
            timeout = Duration.ofMinutes(ecsWaiterTimeout.toLong()),
            retries = ecsWaiterRetries
        )
        log.info("Launched vehicles: ${launchStatus.map { it.vehicleID }}")
        log.info(
            "ECS task IDs: ${launchStatus.map {
                it.taskArn.substringAfterLast('/')
            }.toString().replace(",", "")}"
        )
        log.info("Finished launching")
        return if (simConfigMaps.map { it.simulationMetaData.vehicleId } == launchStatus.map { it.vehicleID }) {
            0
        } else {
            -1
        }
    }
}
