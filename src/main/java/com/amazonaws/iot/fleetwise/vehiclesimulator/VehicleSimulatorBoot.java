package com.amazonaws.iot.fleetwise.vehiclesimulator;

import com.amazonaws.iot.fleetwise.vehiclesimulator.cli.VehicleSimulatorCommand;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;


/*
@SpringBootApplication
public class VehicleSimulatorBoot {//implements CommandLineRunner, ExitCodeGenerator {
*/


 /*private final CommandLine.IFactory factory;
    private final VehicleSimulatorCommand vehicleSimulatorCommand;
    private int exitCode;

    // constructor injection
    VehicleSimulatorBoot(IFactory factory, VehicleSimulatorCommand vehicleSimulatorCommand) {
        this.factory = factory;
        this.vehicleSimulatorCommand = vehicleSimulatorCommand;
    }

    @Override
    public void run(String... args) {
        // let picocli parse command line args and run the business logic
        exitCode = new CommandLine(vehicleSimulatorCommand, factory).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    public static void main(String[] args) {
        // let Spring instantiate and inject dependencies
        System.exit(SpringApplication.exit(SpringApplication.run(VehicleSimulatorBoot.class, args)));
    }



}*/


@SpringBootApplication
public class VehicleSimulatorBoot {
    public static void main(String[] args) {
        SpringApplication.run(VehicleSimulatorBoot.class, args);
    }
}
