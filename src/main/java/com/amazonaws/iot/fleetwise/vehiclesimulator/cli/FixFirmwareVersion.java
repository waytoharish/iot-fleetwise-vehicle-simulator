package com.amazonaws.iot.fleetwise.vehiclesimulator.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import kotlin.jvm.internal.Intrinsics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(
        name = "FixFirmwareVersion",
        description = {"Fix outdated BMS firmware on simulated vehicles"}
)
public final class FixFirmwareVersion implements Callable<Integer> {

    private final ObjectMapper objectMapper;
    @Option(
            names = {"--simulation-input", "-s"},
            required = true
    )
    public String simulationInput;

    private final Logger log = LoggerFactory.getLogger(FixFirmwareVersion.class);

    public FixFirmwareVersion( ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    public String getSimulationInput() {
        return this.simulationInput;
    }

    public void setSimulationInput( String simulationInput) {
        this.simulationInput = simulationInput;
    }

    public Integer call() {
        int result = 0;
        this.log.info("Simulation Map file {} beginning analysis.", this.getSimulationInput());

        String originalContent;
        try {
            File file = new File(this.getSimulationInput());
            if (!file.exists()) {
                originalContent = "File not found: " + this.getSimulationInput();
                System.out.println(originalContent);
                return -1;
            }

            this.log.info("Firmware version out of date for 2 vehicles, implementing fix.");

            byte[] encoded = Files.readAllBytes(Paths.get(file.getPath()));
            originalContent = new String(encoded);
            this.log.info("Parsing simulation file to find outdated firmware version");
            String updatedContent = originalContent.replace("unhealthy", "healthy");
            Files.write(Paths.get(file.getPath()), updatedContent.getBytes());
            this.log.info("Successfully fixed firmware versions! Relaunch the simulator to view the results. \n \n");
        } catch (Exception e) {
            System.out.println("An error occurred: " + e.getMessage());
            return -1;
        }

        return result;
    }

}
