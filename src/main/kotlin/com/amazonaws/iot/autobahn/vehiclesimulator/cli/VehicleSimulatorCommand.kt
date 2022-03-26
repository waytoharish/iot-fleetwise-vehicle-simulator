package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Command
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class,
    description = ["Utility for launching simulated vehicles"],
    subcommands = [
        UploadToS3Command::class,
        LaunchVehicles::class,
        StopVehicles::class
    ]
)
class VehicleSimulatorCommand : Callable<Int> {
    private val log: Logger = LoggerFactory.getLogger(VehicleSimulatorCommand::class.java)
    override fun call(): Int {
        log.info("Please specify commands")
        return 1
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>): Unit = exitProcess(CommandLine(VehicleSimulatorCommand()).execute(*args))
    }
}
