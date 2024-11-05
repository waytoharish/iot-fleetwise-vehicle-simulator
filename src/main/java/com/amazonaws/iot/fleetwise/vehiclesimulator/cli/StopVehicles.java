package com.amazonaws.iot.fleetwise.vehiclesimulator.cli;

import com.amazonaws.iot.fleetwise.vehiclesimulator.SimulationMetaData;
import com.amazonaws.iot.fleetwise.vehiclesimulator.bean.SimulatorCliInput;
import com.amazonaws.iot.fleetwise.vehiclesimulator.VehicleSetupStatus;
import com.amazonaws.iot.fleetwise.vehiclesimulator.VehicleSimulator;
import com.amazonaws.iot.fleetwise.vehiclesimulator.storage.S3Storage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;


import lombok.Data;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@Component
@Command(
        name = "StopVehicles",
        description = {"Stop Virtual Vehicles"}
)
@Data
public final class StopVehicles implements Callable<Integer> {

    private final ObjectMapper objectMapper;
    @Option(
            names = {"--region", "-r"},
            required = true
    )
    public String region;
    @Option(
            names = {"--cpu-architecture", "-a"}
    )

    private String cpuArchitecture;
    @Option(
            names = {"--ecsTaskID"},
            arity = "0..*"
    )

    private List<String> ecsTaskIDs;
    @Option(
            names = {"--simulation-input", "-s"}
    )
    @Nullable
    private String simulationInput;
    @Option(
            names = {"--delete-iot-policy"}
    )
    private boolean deleteIoTPolicy;
    @Option(
            names = {"--delete-iot-certificate"}
    )
    private boolean deleteIoTCert;
    @Option(
            names = {"--ecs-waiter-timeout"},
            required = false
    )
    private int ecsWaiterTimeout;
    @Option(
            names = {"--ecs-waiter-retries"},
            required = false
    )
    private int ecsWaiterRetries;

    private final Logger log = LoggerFactory.getLogger(StopVehicles.class);

    public StopVehicles( ObjectMapper objectMapper) {
        super();

        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
        this.cpuArchitecture = "arm64";
        this.ecsTaskIDs = new ArrayList<>();
        this.ecsWaiterTimeout = 5;
        this.ecsWaiterRetries = 100;
    }


    /*public final String getRegion() {
        return this.region;
    }

    public final void setRegion( String region) {
        this.region = region;
    }


    public final String getCpuArchitecture() {
        return this.cpuArchitecture;
    }

    public final void setCpuArchitecture( String cpuArchitecture) {
        this.cpuArchitecture = cpuArchitecture;
    }


    public final List<String> getEcsTaskIDs() {
        return this.ecsTaskIDs;
    }

    public final void setEcsTaskIDs( List<String> ecsTaskIDs) {
        this.ecsTaskIDs = ecsTaskIDs;
    }

    @Nullable
    public final String getSimulationInput() {
        return this.simulationInput;
    }

    public final void setSimulationInput(@Nullable String var1) {
        this.simulationInput = var1;
    }

    public final boolean getDeleteIoTPolicy() {
        return this.deleteIoTPolicy;
    }

    public final void setDeleteIoTPolicy(boolean var1) {
        this.deleteIoTPolicy = var1;
    }

    public final boolean getDeleteIoTCert() {
        return this.deleteIoTCert;
    }

    public final void setDeleteIoTCert(boolean var1) {
        this.deleteIoTCert = var1;
    }

    public final int getEcsWaiterTimeout() {
        return this.ecsWaiterTimeout;
    }

    public final void setEcsWaiterTimeout(int var1) {
        this.ecsWaiterTimeout = var1;
    }

    public final int getEcsWaiterRetries() {
        return this.ecsWaiterRetries;
    }

    public final void setEcsWaiterRetries(int var1) {
        this.ecsWaiterRetries = var1;
    }*/

    private List<SimulatorCliInput> parseSimulationFile(String simulationPackage) {
        try {
            return objectMapper.readValue(new File(simulationPackage), List.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public Integer call() {
        int result = 0;
        S3AsyncClient s3AsyncClient = S3AsyncClient.builder().region(Region.of(this.getRegion())).build();
        S3Storage s3Storage = new S3Storage(s3AsyncClient);
        final VehicleSimulator testManager = new VehicleSimulator(this.getRegion(), this.cpuArchitecture, s3Storage,null, null,  null, null);
        VehicleSimulator.StopStatus stopStatus = testManager.stopVehicles(ecsTaskIDs, Duration.ofMinutes((long) this.ecsWaiterTimeout), this.ecsWaiterRetries);
        this.log.info("Successfully stopped ecs tasks: {}", stopStatus.getSuccessList());
        if (!stopStatus.getFailedList().isEmpty()) {
            this.log.error("Failed to stop ecs tasks: {}", stopStatus.getFailedList());
            result = -1;
        }

        if (simulationInput != null && !simulationInput.isEmpty()) {
            List<SimulationMetaData> simulationMetaData = parseSimulationFile(simulationInput)
                    .stream().map(SimulatorCliInput::getSimulationMetaData).collect(Collectors.toList());

            VehicleSetupStatus thingDeletionStatus = testManager.clean(
                    simulationMetaData,
                    null,
                    deleteIoTPolicy,
                    deleteIoTCert

            );
            log.info("Successfully deleted things: {}", thingDeletionStatus);
            if (thingDeletionStatus != null && thingDeletionStatus.failedList() !=null && !thingDeletionStatus.failedList().isEmpty()) {
                log.error("Failed to delete things: {}", thingDeletionStatus.failedList());
                result = -1;
            }
            log.info("Finish clean up");
        }
        return result;
    }
}
