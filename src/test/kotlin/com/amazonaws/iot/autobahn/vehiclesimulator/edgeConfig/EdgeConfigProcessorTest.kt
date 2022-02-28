package com.amazonaws.iot.autobahn.vehiclesimulator.edgeConfig

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class EdgeConfigProcessorTest {
    private val edgeConfigProcessor = EdgeConfigProcessor()

    @Test
    fun `When setMqttConnectionParameter called with valid config files`() {
        val configJson =
            """{
                "version": "1.0",
                "networkInterfaces": [],
                "staticConfig": {
                    "bufferSizes": {},
                    "threadIdleTimes": {},
                    "persistency": {},
                    "internalParameters": {},
                    "publishToCloudParameters": {},
                    "mqttConnection": {
                          "endpointUrl": "",
                          "clientId": "",
                          "collectionSchemeListTopic": "",
                          "decoderManifestTopic": "",
                          "canDataTopic": "",
                          "checkinTopic": "",
                          "certificateFilename": "",
                          "privateKeyFilename": ""
                    }
                }
            }"""
        val vehicleIDs = listOf("car1", "car2", "car3", "car4")
        val originalConfigFile = vehicleIDs.associateWith { configJson }
        // Invoke Function to set MQTT Connection Section to the config based on Gamma PDX template
        edgeConfigProcessor.setMqttConnectionParameter(
            jacksonObjectMapper(),
            originalConfigFile,
            "IoTCoreDataEndPointAddress",
            "\$aws/iotfleetwise/gamma-us-east-1/vehicles/\$VEHICLE_ID/collection_schemes",
            "\$aws/iotfleetwise/gamma-us-east-1/vehicles/\$VEHICLE_ID/decoder_manifests",
            "\$aws/iotfleetwise/gamma-us-east-1/vehicles/\$VEHICLE_ID/signals",
            "\$aws/iotfleetwise/gamma-us-east-1/vehicles/\$VEHICLE_ID/checkins",
            "/etc/aws-iot-fleetwise-sim/\$VEHICLE_ID/cert.crt",
            "/etc/aws-iot-fleetwise-sim/\$VEHICLE_ID/pri.key"
        ).map {
            val processedConfig: Config = jacksonObjectMapper().readValue(it.value)
            val processedMqttConnection = processedConfig.staticConfig.mqttConnection
            Assertions.assertEquals(it.key, processedMqttConnection.clientId)
            Assertions.assertEquals("IoTCoreDataEndPointAddress", processedMqttConnection.endPointUrl)
            Assertions.assertEquals("\$aws/iotfleetwise/gamma-us-east-1/vehicles/${it.key}/collection_schemes", processedMqttConnection.collectionSchemeListTopic)
            Assertions.assertEquals("\$aws/iotfleetwise/gamma-us-east-1/vehicles/${it.key}/decoder_manifests", processedMqttConnection.decoderManifestTopic)
            Assertions.assertEquals("\$aws/iotfleetwise/gamma-us-east-1/vehicles/${it.key}/signals", processedMqttConnection.canDataTopic)
            Assertions.assertEquals("\$aws/iotfleetwise/gamma-us-east-1/vehicles/${it.key}/checkins", processedMqttConnection.checkinTopic)
            Assertions.assertEquals("/etc/aws-iot-fleetwise-sim/${it.key}/cert.crt", processedMqttConnection.certificateFilename)
            Assertions.assertEquals("/etc/aws-iot-fleetwise-sim/${it.key}/pri.key", processedMqttConnection.privateKeyFilename)
        }
    }

    @Test
    fun `When setMqttConnectionParameter called with config files missing parameter`() {
        val configJson =
            """{
                "version": "1.0",
                "staticConfig": {
                    "bufferSizes": {},
                    "threadIdleTimes": {},
                    "persistency": {},
                    "internalParameters": {},
                    "publishToCloudParameters": {},
                    "mqttConnection": {
                          "endpointUrl": "",
                          "clientId": "",
                          "collectionSchemeListTopic": "",
                          "decoderManifestTopic": "",
                          "canDataTopic": "",
                          "checkinTopic": "",
                          "certificateFilename": "",
                          "privateKeyFilename": ""
                    }
                }
            }"""
        val vehicleIDs = listOf("car1", "car2", "car3", "car4")
        var originalConfigFile = vehicleIDs.associateWith {
            configJson
        }
        assertThrows<MissingKotlinParameterException> {
            edgeConfigProcessor.setMqttConnectionParameter(
                jacksonObjectMapper(),
                originalConfigFile,
                "IoTCoreDataEndPointAddress",
                "\$aws/iotfleetwise/gamma-us-east-1/vehicles/\$VEHICLE_ID/collection_schemes",
                "\$aws/iotfleetwise/gamma-us-east-1/vehicles/\$VEHICLE_ID/decoder_manifests",
                "\$aws/iotfleetwise/gamma-us-east-1/vehicles/\$VEHICLE_ID/signals",
                "\$aws/iotfleetwise/gamma-us-east-1/vehicles/\$VEHICLE_ID/checkins",
                "/etc/aws-iot-fleetwise-sim/\$VEHICLE_ID/cert.crt",
                "/etc/aws-iot-fleetwise-sim/\$VEHICLE_ID/pri.key"
            )
        }
    }

    @Test
    fun `When setMqttConnectionParameter called with config files with unknown parameter`() {
        val configJson =
            """{
            "version": "1.0",
            "networkInterfaces": [],
            "unknownParameter": [],
            "staticConfig": {
                "bufferSizes": {},
                "threadIdleTimes": {},
                "persistency": {},
                "internalParameters": {},
                "publishToCloudParameters": {},
                "mqttConnection": {
                      "endpointUrl": "",
                      "clientId": "",
                      "collectionSchemeListTopic": "",
                      "decoderManifestTopic": "",
                      "canDataTopic": "",
                      "checkinTopic": "",
                      "certificateFilename": "",
                      "privateKeyFilename": ""
                }
            }
            }"""
        val vehicleIDs = listOf("car1", "car2", "car3", "car4")
        val originalConfigFile = vehicleIDs.associateWith {
            configJson
        }
        assertThrows<UnrecognizedPropertyException> {
            edgeConfigProcessor.setMqttConnectionParameter(
                jacksonObjectMapper(),
                originalConfigFile,
                "IoTCoreDataEndPointAddress",
                "\$aws/iotfleetwise/gamma-us-east-1/vehicles/\$VEHICLE_ID/collection_schemes",
                "\$aws/iotfleetwise/gamma-us-east-1/vehicles/\$VEHICLE_ID/decoder_manifests",
                "\$aws/iotfleetwise/gamma-us-east-1/vehicles/\$VEHICLE_ID/signals",
                "\$aws/iotfleetwise/gamma-us-east-1/vehicles/\$VEHICLE_ID/checkins",
                "/etc/aws-iot-fleetwise-sim/\$VEHICLE_ID/cert.crt",
                "/etc/aws-iot-fleetwise-sim/\$VEHICLE_ID/pri.key"
            )
        }
    }
}
