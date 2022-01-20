package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import picocli.CommandLine
import picocli.CommandLine.Command
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class,
    description = ["Utility for launching simulated vehicles"],
    subcommands = [UploadToS3Command::class]
)
class VehicleSimulatorCommand : Callable<Int> {

    override fun call(): Int {
        // todo switch to logger
        println("Please specify commands")
        return 1
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>): Unit = exitProcess(CommandLine(VehicleSimulatorCommand()).execute(*args))
    }
}
