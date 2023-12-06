package com.amazonaws.iot.autobahn.vehiclesimulator.edgeConfig

import com.amazonaws.iot.autobahn.config.ControlPlaneResources
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
//import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory

/**
 * This class process Edge Config file
 * @param controlPlaneResources is used to query FleetWise control plane topics
 * @param objectMapper is used to convert json string and data class
 */
class EdgeConfigProcessor(
    private val controlPlaneResources: ControlPlaneResources,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(EdgeConfigProcessor::class.java)
    /**
     * This function sets the MQTT Connection parameter for Edge config file
     * The MQTT Connection depends on vehicle ID, FleetWise test environment
     * Note the config file passed in and out as String format
     * @param vehicleIDToConfigMap Mapping between vehicle ID and config string
     * @param iotCoreDeviceDataEndPoint IoT Core Device Data End Point
     * @return Map contains vehicleID and config file filled with mqtt connection parameter
     * @throws MissingKotlinParameterException if user provided config file miss parameter
     * @throws UnrecognizedPropertyException if user provided config file include unknown parameter
     */
    fun setMqttConnectionParameter(
        vehicleIDToConfigMap: Map<String, String>,
        iotCoreDeviceDataEndPoint: String,
    ): Map<String, String> {
        return vehicleIDToConfigMap.mapNotNull {
            // construct mqtt connection based on region, test stage and IoT Core Device End Point
            val mqttConnectionConfig = MqttConnectionConfig(
                iotCoreDeviceDataEndPoint,
                it.key,
                controlPlaneResources.getPolicyTopic(it.key),
                controlPlaneResources.getDecoderManifestTopic(it.key),
                controlPlaneResources.getSignalsTopic(it.key),
                controlPlaneResources.getCheckinTopic(it.key),
                CERTIFICATE_FILE_NAME.replace("VEHICLE_ID", it.key),
                PRIVATE_KEY_FILE_NAME.replace("VEHICLE_ID", it.key)
            )
            val inputConfig = parseInputConfig(it.key, it.value)

            // construct output config with the mqttConnection
            val outputConfig = inputConfig.copy(staticConfig = inputConfig.staticConfig.copy(mqttConnectionConfig = mqttConnectionConfig))
            it.key to objectMapper.writeValueAsString(outputConfig)
        }.toMap()
    }

    /**
     * This function sets the credentialsProvider and s3Upload parameters for Edge config file
     * These settings are only needed for rich-data
     * Note the config file passed in and out as String format
     * @param vehicleIDToConfigMap Mapping between vehicle ID and config string
     * @param roleAliasName the name of the iot role alias
     * @param credentialsProviderEndpoint the endpoint that is used to get the credentials
     * @return Map contains vehicleID and config file filled with mqtt connection parameter
     * @throws MissingKotlinParameterException if user provided config file miss parameter
     * @throws UnrecognizedPropertyException if user provided config file include unknown parameter
     */
    fun setCredentialsProviderParameter(
        vehicleIDToConfigMap: Map<String, String>,
        roleAliasName: String,
        credentialsProviderEndpoint: String
    ): Map<String, String> {
        return vehicleIDToConfigMap.mapNotNull {
            val credentialsProvider = CredentialsProvider(
                endpointUrl = credentialsProviderEndpoint,
                roleAlias = roleAliasName
            )
            val s3Upload = S3Upload(104857600, 5242880, 10)
            val inputConfig = parseInputConfig(it.key, it.value)

            val outputConfig = inputConfig.copy(staticConfig = inputConfig.staticConfig.copy(credentialsProvider = credentialsProvider, s3Upload = s3Upload))
            it.key to objectMapper.writeValueAsString(outputConfig)
        }.toMap()
    }

    private fun parseInputConfig(vehicleName: String, json: String): Config {
        val inputConfig: Config = try {
            objectMapper.readValue(json)
       // } catch (ex: MissingKotlinParameterException) {
       //     log.error("Config File misses Parameter for $vehicleName. Config file: $json")
       //     throw ex
        } catch (ex: UnrecognizedPropertyException) {
            log.error("Config File contains unknown parameter for $vehicleName. Config file: $json")
            throw ex
        }
        return inputConfig
    }

    companion object {
        const val CERTIFICATE_FILE_NAME = "/etc/aws-iot-fleetwise/VEHICLE_ID/cert.crt"
        const val PRIVATE_KEY_FILE_NAME = "/etc/aws-iot-fleetwise/VEHICLE_ID/pri.key"
    }
}
