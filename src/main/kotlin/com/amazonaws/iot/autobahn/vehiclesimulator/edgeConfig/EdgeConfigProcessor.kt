package com.amazonaws.iot.autobahn.vehiclesimulator.edgeConfig

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * This class process Edge Config file
 */
class EdgeConfigProcessor {
    /**
     * This function sets the MQTT Connection parameter for Edge config file
     * The MQTT Connection depends on vehicle ID, FleetWise test environment
     * Note the config file passed in and out as String format
     * @param objectMapper ObjectMapper instance
     * @param vehicleIDToConfigMap Mapping between vehicle ID and config string
     * @param iotCoreDeviceDataEndPoint IoT Core Device Data End Point
     * @param collectionSchemeListTopic MQTT Topic for Collection Scheme List
     * @param decoderManifestTopic MQTT Topic for decoder manifest
     * @param canDataTopic MQTT Topic for can data
     * @param checkinTopic MQTT Topic for checkin
     * @param certificateFileName Path to certificate file
     * @param privateKeyFileName Path to the privatfe key file
     * @return Map contains vehicleID and config file filled with mqtt connection parameter
     * @throws MissingKotlinParameterException if user provided config file miss parameter
     */
    fun setMqttConnectionParameter(
        objectMapper: ObjectMapper,
        vehicleIDToConfigMap: Map<String, String>,
        iotCoreDeviceDataEndPoint: String,
        collectionSchemeListTopic: String,
        decoderManifestTopic: String,
        canDataTopic: String,
        checkinTopic: String,
        certificateFileName: String,
        privateKeyFileName: String
    ): Map<String, String> {
        return vehicleIDToConfigMap.mapNotNull {
            // construct mqtt connection based on input
            val mqttConnection = MqttConnection(
                iotCoreDeviceDataEndPoint,
                it.key,
                collectionSchemeListTopic.replace("\$VEHICLE_ID", it.key),
                decoderManifestTopic.replace("\$VEHICLE_ID", it.key),
                canDataTopic.replace("\$VEHICLE_ID", it.key),
                checkinTopic.replace("\$VEHICLE_ID", it.key),
                certificateFileName.replace("\$VEHICLE_ID", it.key),
                privateKeyFileName.replace("\$VEHICLE_ID", it.key),
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
            val outputConfig = inputConfig.copy(staticConfig = inputConfig.staticConfig.copy(mqttConnection = mqttConnection))
            it.key to objectMapper.writeValueAsString(outputConfig)
        }.toMap()
    }
}
