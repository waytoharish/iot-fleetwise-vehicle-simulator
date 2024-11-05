package com.amazonaws.iot.fleetwise.vehiclesimulator.cli;

import java.util.Arrays;
import java.util.concurrent.Callable;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Component
@Command(
        subcommands = {UploadToS3Command.class, LaunchVehicles.class, StopVehicles.class, FixFirmwareVersion.class},
        versionProvider = VersionProvider.class,
        mixinStandardHelpOptions = true,
        description = {"Utility for launching simulated vehicles"}
)
public final class VehicleSimulatorCommand implements Callable<Integer> {

    private final Logger log = LoggerFactory.getLogger(VehicleSimulatorCommand.class);


    public Integer call() {
        this.log.info("FleetWise Vehicle Simulator \n");
        this.log.info("To continue, please select one of the following options when running this command: LaunchVehicles, StopVehicles, or FixFirmwareVersion \n");
        return 0;
    }

    public static void main(@NotNull String[] args) {
        System.exit((new CommandLine(new VehicleSimulatorCommand())).execute((String[]) Arrays.copyOf(args, args.length)));
        throw new RuntimeException("System.exit returned normally, while it was supposed to halt JVM.");
    }

}