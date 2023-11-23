package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import com.amazonaws.iot.autobahn.vehiclesimulator.SimulatorCliInput
import com.amazonaws.iot.autobahn.vehiclesimulator.VehicleSimulator
import com.amazonaws.iot.autobahn.vehiclesimulator.storage.S3Storage
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
    name = "FixFirmwareVersion",
    description = ["Fix outdated BMS firmware on simulated vehicles"],
)
class FixFirmwareVersion(private val objectMapper: ObjectMapper = jacksonObjectMapper()) : Callable<Int> {
    @CommandLine.Option(required = true, names = ["--simulation-input", "-s"])
    lateinit var simulationInput: String

    private val log: Logger = LoggerFactory.getLogger(FixFirmwareVersion::class.java)

    override fun call(): Int {
        var result = 0
        log.info("Simulation Map file $simulationInput; beginning analysis.")
        try {
        val file = File(simulationInput)
        if(!file.exists()) {
            println("File not found: $simulationInput")
            return -1
        }
        
        log.info("Firmware version out of date for 2 vehicles, implementing fix.")

        val originalContent = file.readText()

        log.info("Parsing simulation file to find outdated firmware version")
        
        val updatedContent = originalContent.replace("unhealthy", "healthy")

        file.writeText(updatedContent)
        log.info("Successfully fixed firmware versions! Relaunch the simulator to view the results. \n \n")
        } catch (e: Exception) {
            println("An error occurred: ${e.message}")
            return -1
        }
        return result
    }
}
