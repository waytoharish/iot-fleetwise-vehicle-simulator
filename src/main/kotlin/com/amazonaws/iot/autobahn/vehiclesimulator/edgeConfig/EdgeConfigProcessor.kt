package com.amazonaws.iot.autobahn.vehiclesimulator.edgeConfig

import com.google.gson.Gson
import java.io.BufferedReader

/**
 * This class process Edge Config file
 */
class EdgeConfigProcessor {
    /**
     * This function set the MQTT Connection parameter for Edge config file
     * The MQTT Connection Template for each test environment is stored under resources
     * Note the config file passed in and out in String format
     * @param testEnvironment The FleetWise test environment such as Gamma_PDX, Gamma_IAD, Beta_IAD, Alpha_IAD, Prod_IAD, Prod_PDX
     * @param vehicleIDToConfigMap Mapping between vehicle ID and config string
     * @param iotCoreDeviceDataEndPoint IoT Core Device Data End Point
     * @return Map contains vehicleID and config file filled with mqtt connection parameter
     */
    fun setMqttConnectionParameter(
        testEnvironment: String,
        vehicleIDToConfigMap: Map<String, String>,
        iotCoreDeviceDataEndPoint: String
    ): Map<String, String> {
        return vehicleIDToConfigMap.mapNotNull {
            val gson = Gson()
            val mqttConnectionFilePath = try {
                this.javaClass.classLoader
                    .getResourceAsStream("mqttConnection/$testEnvironment.json")!!
                    .bufferedReader()
                    .use(BufferedReader::readText)
            } catch (ex: NullPointerException) {
                println("The provided Edge config file is invalid: $testEnvironment")
                throw ex
            }
            // This is the MQTT config JSON string for the targeted test environment
            val mqttConfigTemplate = gson.fromJson(mqttConnectionFilePath, Map::class.java)
            // Convert JSON string to Map
            @Suppress("UNCHECKED_CAST")
            val mqttConnectionParameterTemplate = mqttConfigTemplate["mqttConnection"] as Map<String, String>
            // Replace the templated value with targeted test environment
            val mqttConnectionParameterMap = mqttConnectionParameterTemplate.map { parameter ->
                when (parameter.key) {
                    "endpointUrl" -> parameter.key to iotCoreDeviceDataEndPoint
                    else -> parameter.key to parameter.value.replace("\$VEHICLE_ID", it.key)
                }
            }.toMap()
            // Convert the config file provided by user to Map
            @Suppress("UNCHECKED_CAST")
            val userConfigJson = try {
                gson.fromJson(it.value, Map::class.java) as MutableMap<String, MutableMap<String, Map<String, String>>>
            } catch (ex: NullPointerException) {
                // TODO Log Error
                println("The provided Edge config file is invalid: ${it.value}")
                throw ex
            }
            // Before we fill in the mqttConnection, double check user config format is correct
            // Note this program doesn't detect invalid parameter inside mqttConnection section
            if (userConfigJson["staticConfig"] is MutableMap<String, Map<String, String>> &&
                userConfigJson["staticConfig"]?.get("mqttConnection") is Map<String, String>
            ) {
                userConfigJson["staticConfig"]?.put("mqttConnection", mqttConnectionParameterMap)
                it.key to gson.toJson(userConfigJson)
            } else {
                // TODO Log Error
                println("The provided Edge config file is invalid: ${it.value}")
                null
            }
        }.toMap()
    }
}
