package com.amazonaws.iot.autobahn.vehiclesimulator.edgeConfig

import com.amazonaws.iot.autobahn.config.ControlPlaneResources
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * This class process Edge Config file
 * @param controlPlaneResources is used to query FleetWise control plane topics
 * @param objectMapper is used to convert json string and data class
 */
class EdgeConfigProcessor(
    private val controlPlaneResources: ControlPlaneResources,
    private val objectMapper: ObjectMapper
) {
    /**
     * This function sets the MQTT Connection parameter for Edge config file
     * The MQTT Connection depends on vehicle ID, FleetWise test environment
     * Note the config file passed in and out as String format
     * @param vehicleIDToConfigMap Mapping between vehicle ID and config string
     * @param iotCoreDeviceDataEndPoint IoT Core Device Data End Point
     * @return Map contains vehicleID and config file filled with mqtt connection parameter
     * @throws MissingKotlinParameterException if user provided config file miss parameter
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
            val inputConfig: Config = try {
                objectMapper.readValue(it.value)
            } catch (ex: MissingKotlinParameterException) {
                println("Config File misses Parameter for ${it.key}. Config file: ${it.value}")
                throw ex
            } catch (ex: UnrecognizedPropertyException) {
                println("Config File contains unknown parameter for ${it.key}. Config file: ${it.value}")
                throw ex
            }
            // construct output config with the mqttConnection
            val outputConfig = inputConfig.copy(staticConfig = inputConfig.staticConfig.copy(mqttConnectionConfig = mqttConnectionConfig))
            it.key to objectMapper.writeValueAsString(outputConfig)
        }.toMap()
    }

    companion object {
        const val CERTIFICATE_FILE_NAME = "/etc/aws-iot-fleetwise/VEHICLE_ID/cert.crt"
        const val PRIVATE_KEY_FILE_NAME = "/etc/aws-iot-fleetwise/VEHICLE_ID/pri.key"
    }
}
