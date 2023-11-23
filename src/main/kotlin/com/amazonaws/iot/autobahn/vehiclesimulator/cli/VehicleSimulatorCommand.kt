package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Command
import java.util.concurrent.Callable
import kotlin.system.exitProcess


class KotlinApp {

    public fun getGreeting() : String {
        return "Hello World from Kotlin!";
    }}

/*fun main() {
    print(KotlinApp().getGreeting())
} */

@Command(
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class,
    description = ["Utility for launching simulated vehicles"],
    subcommands = [
        UploadToS3Command::class,
        LaunchVehicles::class,
        StopVehicles::class,
        FixFirmwareVersion::class
    ]
)

class VehicleSimulatorCommand : Callable<Int> {
    private val log: Logger = LoggerFactory.getLogger(VehicleSimulatorCommand::class.java)
    override fun call(): Int {
        log.info("FleetWise Vehicle Simulator \n")
        log.info("To continue, please select one of the following options when running this command: LaunchVehicles, StopVehicles, or FixFirmwareVersion \n")
        
        return 1
    }

    //companion object {
        //@JvmStatic
      
    //}
}

fun main(args: Array<String>) {
    exitProcess(CommandLine(VehicleSimulatorCommand()).execute(*args))
} 




