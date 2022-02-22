package com.amazonaws.iot.autobahn.vehiclesimulator.edgeConfig

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class EdgeConfigProcessorTest {
    private val edgeConfigProcessor = EdgeConfigProcessor()

    @Test
    fun `When setMqttConnectionParameter called with valid config files`() {
        val gson = Gson()
        val vehicleIDs = listOf("car1", "car2", "car3", "car4")
        val originalConfigFile = vehicleIDs.associateWith {
            // Here we construct a valid config file for EdgeConfig module to process
            gson.toJson(
                mapOf(
                    "staticConfig"
                        to mapOf(
                            "mqttConnection"
                                to mapOf(
                                    "endpointUrl" to "TBD",
                                    "clientId" to "\$VEHICLE_ID",
                                    "topic" to "\$topic_prefix/\$VEHICLE_ID/topic_suffix"
                                )
                        )
                )
            )
        }
        // Invoke Function to set MQTT Connection Section to the config based on Gamma PDX template
        edgeConfigProcessor.setMqttConnectionParameter(
            "Gamma_PDX",
            originalConfigFile,
            "IoTCoreDataEndPointAddress"
        ).map {
            @Suppress("UNCHECKED_CAST")
            val processedConfigJson = gson.fromJson(it.value, Map::class.java) as MutableMap<String, MutableMap<String, Map<String, String>>>
            Assertions.assertEquals(
                "IoTCoreDataEndPointAddress",
                processedConfigJson["staticConfig"]?.get("mqttConnection")?.get("endpointUrl")
            )
            Assertions.assertEquals(it.key, processedConfigJson["staticConfig"]?.get("mqttConnection")?.get("clientId"))
            Assertions.assertEquals(
                it.key,
                processedConfigJson["staticConfig"]?.get("mqttConnection")?.get("collectionSchemeListTopic")
                    ?.substringAfter("\$aws/iotfleetwise/gamma-us-west-2/vehicles/")?.substringBefore("/collection_schemes")
            )
        }
    }

    @Test
    fun `When setMqttConnectionParameter called with invalid config files`() {
        val gson = Gson()
        val vehicleIDs = listOf("car1", "car2", "car3", "car4")
        var originalConfigFile = vehicleIDs.associateWith {
            // Here we construct a config file without staticConfig section
            gson.toJson(
                mapOf(
                    "invalidStaticConfig"
                        to "invalid_parameter"
                )
            )
        }
        var processedConfigMap = edgeConfigProcessor.setMqttConnectionParameter(
            "Gamma_PDX",
            originalConfigFile,
            "IoTCoreDataEndPointAddress"
        )
        Assertions.assertTrue(processedConfigMap.isEmpty())
        originalConfigFile = vehicleIDs.associateWith {
            // Here we construct a config file without mqttConnection
            gson.toJson(
                mapOf(
                    "staticConfig"
                        to "invalid_parameter"
                )
            )
        }
        processedConfigMap = edgeConfigProcessor.setMqttConnectionParameter(
            "Gamma_PDX",
            originalConfigFile,
            "IoTCoreDataEndPointAddress"
        )
        Assertions.assertTrue(processedConfigMap.isEmpty())
        originalConfigFile = vehicleIDs.associateWith {
            // Here we construct a config file with invalid mqttConnection
            gson.toJson(
                mapOf(
                    "staticConfig"
                        to "mqttConnection"
                )
            )
        }
        processedConfigMap = edgeConfigProcessor.setMqttConnectionParameter(
            "Gamma_PDX",
            originalConfigFile,
            "IoTCoreDataEndPointAddress"
        )
        Assertions.assertTrue(processedConfigMap.isEmpty())
        originalConfigFile = vehicleIDs.associateWith {
            // Here we construct a invalid empty config file
            ""
        }
        // We shall expect an exception being thrown out
        assertThrows<NullPointerException> {
            edgeConfigProcessor.setMqttConnectionParameter(
                "Gamma_PDX",
                originalConfigFile,
                "IoTCoreDataEndPointAddress"
            )
        }
    }

    @Test
    fun `When setMqttConnectionParameter called with invalid test environment`() {
        val gson = Gson()
        val vehicleIDs = listOf("car1", "car2", "car3", "car4")
        var originalConfigFile = vehicleIDs.associateWith {
            // Here we construct a config file without staticConfig section
            gson.toJson(
                mapOf(
                    "invalidStaticConfig"
                        to "invalid_parameter"
                )
            )
        }
        // We shall expect an exception being thrown out due to invalid test environment
        assertThrows<NullPointerException> {
            edgeConfigProcessor.setMqttConnectionParameter(
                "Gamma_XXX",
                originalConfigFile,
                "IoTCoreDataEndPointAddress"
            )
        }
    }
}
