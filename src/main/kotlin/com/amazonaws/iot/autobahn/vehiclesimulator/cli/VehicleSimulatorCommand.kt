package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import picocli.CommandLine
import picocli.CommandLine.Command

@Command(
    name = "checksum",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class,
    description = ["Utility for launching simulated vehicles"]
)
class VehicleSimulatorCommand : Runnable {

    override fun run() {
        // todo switch to logger
        println("IoT FleetWise vehicle simulation utility")
    }

    companion object {
        @kotlin.jvm.JvmStatic
        fun main(args: Array<String>) {
            CommandLine(VehicleSimulatorCommand()).execute(*args)
        }
    }
}
