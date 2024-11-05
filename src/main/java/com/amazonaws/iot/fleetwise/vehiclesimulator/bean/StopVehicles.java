package com.amazonaws.iot.fleetwise.vehiclesimulator.bean;


import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.Nullable;


import java.io.Serializable;
import java.util.List;

@Data
@Jacksonized
@Builder
public class StopVehicles implements Serializable {

    @JsonProperty(value = "region", required = true)
    public String region;

    @JsonProperty(value = "cpuArchitecture")
    private String cpuArchitecture;


    @JsonProperty(value = "ecsTaskIDs")
    private List<String> ecsTaskIDs;


    @Nullable
    @JsonProperty(value = "simulationInput")
    private String simulationInput;


    @JsonProperty(value = "deleteIoTPolicy")
    private boolean deleteIoTPolicy;


    @JsonProperty(value = "deleteIoTCert")
    private boolean deleteIoTCert;

    @JsonProperty(value = "ecsWaiterTimeout")
    private int ecsWaiterTimeout;


    @JsonProperty(value = "ecsWaiterRetries")
    private int ecsWaiterRetries;
}
