package com.amazonaws.iot.autobahn.vehiclesimulator

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * SimulatorCliInput defines the inputs to Vehicle Simulator CLI to start / stop/ cleanup simulation per vehicle
 */
data class SimulatorCliInput(
    @JsonProperty("simulationMetaData")
    val simulationMetaData: SimulationMetaData,
    @JsonProperty("edgeConfigFilename")
    val configPath: String,
    @JsonProperty("simulationScriptPath")
    val localPath: String
)

/**
 * SimulationMetaData contains the minimum meta data for vehicle simulation
 */
data class SimulationMetaData(
    @JsonProperty("vehicleID")
    val vehicleId: String,
    @JsonProperty("s3")
    val s3: S3
)

data class S3(
    @JsonProperty("bucket")
    val bucket: String,
    @JsonProperty("key")
    val key: String
)
