package com.amazonaws.iot.autobahn.vehiclesimulator

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * SimulatorCliInput defines the inputs to Vehicle Simulator CLI to start / stop/ cleanup simulation per vehicle
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
@JsonIgnoreProperties(ignoreUnknown = true)
data class SimulationMetaData(

    @JsonProperty("vehicleID")
    val vehicleId: String,

    @JsonProperty("s3")
    val s3: S3,

    @JsonProperty("provisionThing")
    var provisionThing: Boolean = true,

    @JsonProperty("deviceCertificateConfig")
    var deviceCertificateConfig: DeviceCertificateConfig? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DeviceCertificateConfig(

    @JsonProperty("pcaArn")
    val pcaArn: String,

    @JsonProperty("commonName")
    val commonName: String,

    @JsonProperty("organization")
    val organization: String,

    @JsonProperty("countryCode")
    val countryCode: String,

    @JsonProperty("validityDays")
    val validityDays: Long = 120
)

data class S3(
    @JsonProperty("bucket")
    val bucket: String,
    @JsonProperty("key")
    val key: String
)
