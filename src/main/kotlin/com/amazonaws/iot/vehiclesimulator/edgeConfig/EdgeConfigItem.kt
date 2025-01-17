package com.amazonaws.iot.fleetwise.vehiclesimulator.edgeConfig

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * This package contains data class definition for Edge config file.
 * In the future, if we generate config file from Control Plane, those data class shall come from one source.
 */
data class Config(
    @JsonProperty("version")
    val version: String,
    @JsonProperty("networkInterfaces")
    val networkInterfaces: Any,
    @JsonProperty("staticConfig")
    val staticConfig: StaticConfig
)

data class StaticConfig(
    @JsonProperty("bufferSizes")
    val bufferSizes: Any,
    @JsonProperty("threadIdleTimes")
    val threadIdleTimes: Any,
    @JsonProperty("persistency")
    val persistency: Any,
    @JsonProperty("internalParameters")
    val internalParameters: Any,
    @JsonProperty("publishToCloudParameters")
    val publishToCloudParameters: Any,
    @JsonProperty("mqttConnection")
    val mqttConnectionConfig: MqttConnectionConfig,
    @JsonProperty("credentialsProvider")
    val credentialsProvider: CredentialsProvider?,
    @JsonProperty("s3Upload")
    val s3Upload: S3Upload?
)

data class S3Upload(
    @JsonProperty("maxEnvelopeSize")
    val maxEnvelopeSize: Long,
    @JsonProperty("multipartSize")
    val multipartSize: Long,
    @JsonProperty("maxConnections")
    val maxConnections: Long
)

data class CredentialsProvider(
    @JsonProperty("endpointUrl")
    val endpointUrl: String,
    @JsonProperty("roleAlias")
    val roleAlias: String,
)

data class MqttConnectionConfig(
    @JsonProperty("endpointUrl")
    val endPointUrl: String,
    @JsonProperty("clientId")
    val clientId: String,
    @JsonProperty("collectionSchemeListTopic")
    val collectionSchemeListTopic: String,
    @JsonProperty("decoderManifestTopic")
    val decoderManifestTopic: String,
    @JsonProperty("canDataTopic")
    val canDataTopic: String,
    @JsonProperty("checkinTopic")
    val checkinTopic: String,
    @JsonProperty("certificateFilename")
    val certificateFilename: String,
    @JsonProperty("privateKeyFilename")
    val privateKeyFilename: String
)
