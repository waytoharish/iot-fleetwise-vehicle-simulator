package com.amazonaws.iot.fleetwise.vehiclesimulator.bean;
import com.amazonaws.iot.fleetwise.vehiclesimulator.SimulationMetaData;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@JsonIgnoreProperties(
        ignoreUnknown = true
)
@Data
@Jacksonized
@Builder
public class SimulatorCliInput {

      @JsonProperty("simulationMetaData")
      private SimulationMetaData simulationMetaData;

      @JsonProperty("edgeConfigFilename")
      private  String configPath;

      @JsonProperty("simulationScriptPath")
      private  String localPath;
}
