package com.amazonaws.iot.autobahn.vehiclesimulator.edgeConfig

import com.amazonaws.iot.autobahn.config.ControlPlaneResources
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class EdgeConfigProcessorTest {
    private val controlPlaneResources = mockk<ControlPlaneResources>()
    private val edgeConfigProcessor = EdgeConfigProcessor(controlPlaneResources, jacksonObjectMapper())

    @BeforeEach
    fun setup() {
        every {
            controlPlaneResources.getCheckinTopic(any())
        } returns "checkin-topic"
        every {
            controlPlaneResources.getPolicyTopic(any())
        } returns "collection-scheme-topic"
        every {
            controlPlaneResources.getDecoderManifestTopic(any())
        } returns "decoder-manifest-topic"
        every {
            controlPlaneResources.getSignalsTopic(any())
        } returns "signal-topic"
    }

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
        val inputConfigFileMap = vehicleIDs.associateWith { configJson }
        // Invoke Function to set MQTT Connection Section to the config based on Gamma PDX template
        edgeConfigProcessor.setMqttConnectionParameter(
            inputConfigFileMap,
            "IoTCoreDataEndPointAddress",
        ).map {
            val processedConfig: Config = jacksonObjectMapper().readValue(it.value)
            val processedMqttConnection = processedConfig.staticConfig.mqttConnectionConfig
            Assertions.assertEquals(it.key, processedMqttConnection.clientId)
            Assertions.assertEquals("IoTCoreDataEndPointAddress", processedMqttConnection.endPointUrl)
            Assertions.assertEquals("collection-scheme-topic", processedMqttConnection.collectionSchemeListTopic)
            Assertions.assertEquals("decoder-manifest-topic", processedMqttConnection.decoderManifestTopic)
            Assertions.assertEquals("signal-topic", processedMqttConnection.canDataTopic)
            Assertions.assertEquals("checkin-topic", processedMqttConnection.checkinTopic)
            Assertions.assertEquals(
                EdgeConfigProcessor.CERTIFICATE_FILE_NAME.replace("VEHICLE_ID", it.key),
                processedMqttConnection.certificateFilename
            )
            Assertions.assertEquals(
                EdgeConfigProcessor.PRIVATE_KEY_FILE_NAME.replace("VEHICLE_ID", it.key),
                processedMqttConnection.privateKeyFilename
            )
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
        val originalConfigFile = vehicleIDs.associateWith {
            configJson
        }
        assertThrows<MissingKotlinParameterException> {
            edgeConfigProcessor.setMqttConnectionParameter(
                originalConfigFile,
                "IoTCoreDataEndPointAddress",
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
                originalConfigFile,
                "IoTCoreDataEndPointAddress",
            )
        }
    }

    @Test
    fun `When setCredentialsProviderParameter called with valid config files`() {
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
                    "s3Upload": {
                          "maxEnvelopeSize": 500
                    },
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
        val inputConfigFileMap = vehicleIDs.associateWith { configJson }
        // Invoke Function to set MQTT Connection Section to the config based on Gamma PDX template
        edgeConfigProcessor.setCredentialsProviderParameter(
            inputConfigFileMap,
            "UnitTestRoleAlias",
            "credentials.unit-test.endpoint"
        ).map {
            val processedConfig: Config = jacksonObjectMapper().readValue(it.value)
            val processedCredentialsProvider = processedConfig.staticConfig.credentialsProvider
            val processedS3Upload = processedConfig.staticConfig.s3Upload
            Assertions.assertEquals(10, processedS3Upload!!.maxConnections)
            Assertions.assertEquals("credentials.unit-test.endpoint", processedCredentialsProvider!!.endpointUrl)
        }
    }
}
