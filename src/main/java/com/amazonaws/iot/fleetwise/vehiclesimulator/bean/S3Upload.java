package com.amazonaws.iot.fleetwise.vehiclesimulator.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import picocli.CommandLine;

import java.io.Serializable;

@Data
@Jacksonized
@Builder
public class S3Upload implements Serializable {
    @JsonProperty(value = "bucket", required = true)
    public String bucket;

    @JsonProperty(value = "key", required = true)
    public String key;

    @JsonProperty(value = "data", required = true)
    public String data;


    @JsonProperty(value = "region", required = true)
    public String region;
}
