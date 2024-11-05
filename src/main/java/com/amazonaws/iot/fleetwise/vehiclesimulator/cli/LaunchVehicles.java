package com.amazonaws.iot.fleetwise.vehiclesimulator.cli;

import com.amazonaws.iot.fleetwise.vehiclesimulator.bean.SimulatorCliInput;
import com.amazonaws.iot.fleetwise.vehiclesimulator.VehicleSetupStatus;
import com.amazonaws.iot.fleetwise.vehiclesimulator.VehicleSimulator;
import com.amazonaws.iot.fleetwise.vehiclesimulator.exceptions.EcsTaskManagerException;
import com.amazonaws.iot.fleetwise.vehiclesimulator.storage.S3Storage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@Component
@Command(
        name = "LaunchVehicles",
        description = {"Launch Virtual Vehicles and start simulation"}
)
@Data
public final class LaunchVehicles implements Callable<Integer> {

    @Option(
            names = {"--simulation-input", "-s"},
            required = true
    )
    public String simulationInput;

    @Option(
            names = {"--region", "-r"},
            required = true
    )
    public String region;

    @Option(
            names = {"--decoder-manifest-arn", "-d"},
            required = true
    )
    public String decoderManifestArn;

    @Option(
            names = {"--vehicle-model-arn", "-m"},
            required = true
    )
    public String vehicleModelArn;

    @Option(
            names = {"--createVehicles", "-c"},
            required = false
    )
    private boolean createVehicles;

    @Option(
            names = {"--tag", "-t"},
            required = false,
            arity = "0..*"
    )
    private List<String> tags;

    @Option(
            names = {"--cpu-architecture", "-a"},
            required = false
    )
    private String cpuArchitecture;

    @Option(
            names = {"--recreate-iot-policy"},
            required = false
    )
    private boolean recreateIoTPolicyIfExists;

    @Option(
            names = {"--ecs-task-definition"},
            required = false
    )
    private String ecsTaskDefinition;

    @Option(
            names = {"--ecs-waiter-timeout", "-w"},
            required = false
    )
    private int ecsWaiterTimeout;

    @Option(
            names = {"--ecs-waiter-retries"},
            required = false
    )
    private int ecsWaiterRetries;

    private final ObjectMapper objectMapper;

    private final Logger log = LoggerFactory.getLogger(LaunchVehicles.class);

    public LaunchVehicles() {
        this(new ObjectMapper());
    }

    public LaunchVehicles(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.tags = new ArrayList<>();
        this.cpuArchitecture = "amd64";
        this.ecsTaskDefinition = "fwe-" + this.cpuArchitecture + "-with-cw";
        this.ecsWaiterTimeout = 5;
        this.ecsWaiterRetries = 100;
    }


   /* public String getSimulationInput() {
        return this.simulationInput;
    }

    public void setSimulationInput( String simulationInput) {
        this.simulationInput = simulationInput;
    }


    public String getRegion() {
        return this.region;
    }

    public void setRegion( String region) {
        this.region = region;
    }


    public final String getDecoderManifestArn() {
        return this.decoderManifestArn;
    }

    public void setDecoderManifestArn( String decoderManifestArn) {
        this.decoderManifestArn = decoderManifestArn;
    }


    public final String getVehicleModelArn() {
        return this.vehicleModelArn;
    }

    public final void setVehicleModelArn( String vehicleModelArn) {
        this.vehicleModelArn = vehicleModelArn;
    }

    public boolean getCreateVehicles() {
        return this.createVehicles;
    }

    public void setCreateVehicles(boolean createVehicles) {
        this.createVehicles = createVehicles;
    }


    public List<String> getTags() {
        return this.tags;
    }

    public void setTags( List<String> tags) {
        this.tags = tags;
    }


    public String getCpuArchitecture() {
        return this.cpuArchitecture;
    }

    public void setCpuArchitecture( String cpuArchitecture) {
        this.cpuArchitecture = cpuArchitecture;
    }

    public final boolean getRecreateIoTPolicyIfExists() {
        return this.recreateIoTPolicyIfExists;
    }

    public final void setRecreateIoTPolicyIfExists(boolean var1) {
        this.recreateIoTPolicyIfExists = var1;
    }


    public final String getEcsTaskDefinition() {
        return this.ecsTaskDefinition;
    }

    public final void setEcsTaskDefinition( String ecsTaskDefinition) {
        this.ecsTaskDefinition = ecsTaskDefinition;
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
            return objectMapper.readValue(new File(simulationPackage), new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void  uploadLocalSimFiles( List<SimulatorCliInput> simConfigMaps, S3Storage s3Storage) {
        simConfigMaps.stream().filter(it -> !it.getLocalPath().isEmpty())
                .forEach(
                        simConfig -> {
                            try {
                                List<Path> list = Files.walk(Paths.get(simConfig.getLocalPath()))
                                        .filter(Files::isRegularFile).toList();
                                list.forEach(it -> {
                                    File file = new File(it.toAbsolutePath().toString());
                                    byte[] fileStream = null;
                                    try {
                                        fileStream = Files.readAllBytes(Paths.get(file.getPath()));
                                        s3Storage.put(simConfig.getSimulationMetaData().getS3().getBucket(), simConfig.getSimulationMetaData().getS3().getKey() +"/sim/" + Paths.get(simConfig.getLocalPath()).relativize(it), fileStream);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                                );
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                );
    }

    private Map<String, String> readConfigFile(List<SimulatorCliInput> simConfigMaps) {
        return simConfigMaps.stream().collect(Collectors.toMap(item -> item.getSimulationMetaData().getVehicleId(),item -> {
            try {
                return new String(Files.readAllBytes(Paths.get(item.getConfigPath())));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }


    public Integer call() throws EcsTaskManagerException {

        this.log.info("Simulation Map file {} and region {} and platform {}", this.getSimulationInput(), this.getRegion(),this.cpuArchitecture);
        final List<SimulatorCliInput> simConfigMaps = parseSimulationFile(this.getSimulationInput());
        final Map<String, String> edgeConfigFiles = this.readConfigFile(simConfigMaps);

        S3Storage s3Storage = new S3Storage(S3AsyncClient.builder().region(Region.of(region)).build());

        this.log.info("Uploading simulation files to S3");
        uploadLocalSimFiles(simConfigMaps, s3Storage);

        VehicleSimulator vehicleSimulator = new VehicleSimulator(region, cpuArchitecture,
                s3Storage, null, null, null, null);

        VehicleSetupStatus thingCreationStatus = vehicleSimulator.preLaunch(
                objectMapper,
                simConfigMaps.stream().map( SimulatorCliInput::getSimulationMetaData ).collect(Collectors.toList()),
                edgeConfigFiles,
                null,
                null,
                recreateIoTPolicyIfExists,
                null,
                decoderManifestArn,
                vehicleModelArn,
                createVehicles
        );
        log.info("Set up vehicles: {}", thingCreationStatus.successList());

        if (!thingCreationStatus.failedList().isEmpty()) {
            log.error("Failed to setup vehicles: {}", thingCreationStatus.failedList());
            log.error("Cannot continue simulation as not all things are created successful");
            return -1;
        }
        List<VehicleSimulator.LaunchStatus>  launchStatus = vehicleSimulator.launchVehicles(
                simConfigMaps.stream().map(SimulatorCliInput::getSimulationMetaData).collect(Collectors.toList()),
                ecsTaskDefinition, "ubuntu-"+ cpuArchitecture +"-capacity-provider",
                tags.stream().collect(Collectors.toMap(Function.identity(), item -> item)),
                Duration.ofMinutes(ecsWaiterTimeout),
                ecsWaiterRetries
        );

        log.info("Launched vehicles: {}", launchStatus);
        log.info("Finished launching");


        List<String> list1 = simConfigMaps.stream().map(it -> it.getSimulationMetaData().getVehicleId()).toList();
        List<String> list2 = launchStatus.stream().map(VehicleSimulator.LaunchStatus::getVehicleID).toList();
        if (list1.equals(list2)) {
            return 0;
        } else {
            return -1;
        }
    }



}
