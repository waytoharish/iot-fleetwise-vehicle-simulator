package com.amazonaws.iot.fleetwise.vehiclesimulator.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Jacksonized
@Builder
public class LaunchVehicle implements Serializable {

    @JsonProperty(value= "vehicleNamePrefix" ,required = true)
    public String vehicleNamePrefix;

    @JsonProperty(value = "simulationInput", required = true)
    public String simulationInput;

    @JsonProperty(value = "region", required = true)
    public String region;

    @JsonProperty(value = "decoderManifestArn", required = true)
    public String decoderManifestArn;

    @JsonProperty(value = "vehicleModelArn", required = true)
    public String vehicleModelArn;

    @JsonProperty(value = "createVehicles", required = true)
    private boolean createVehicles;
    
    @JsonProperty(value ="tags", required = true)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @JsonProperty(value = "cpuArchitecture", required = true)
    private String cpuArchitecture;

    @JsonProperty(value = "recreateIoTPolicy", required = true)
    private boolean recreateIoTPolicyIfExists;

    @JsonProperty(value = "ecsTaskDefinition", required = true)
    private String ecsTaskDefinition;

   
    @JsonProperty(value = "ecsWaiterTimeout", required = true)
    @Builder.Default
    private int ecsWaiterTimeout=5;

    
    @JsonProperty(value = "ecsWaiterRetries", required = true)
    @Builder.Default
    private int ecsWaiterRetries=100;

    @JsonProperty(value = "vehicleToProvision", required = true)
    @Builder.Default
    private int vehicleToProvision=1;

    @JsonProperty(value = "s3", required = true)
    private S3Upload s3Upload;

}
