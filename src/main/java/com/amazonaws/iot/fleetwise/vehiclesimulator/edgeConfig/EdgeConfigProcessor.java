package com.amazonaws.iot.fleetwise.vehiclesimulator.edgeConfig;

import com.amazonaws.iot.fleetwise.vehiclesimulator.config.ControlPlaneResources;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import java.util.*;
import java.util.stream.Collectors;

import kotlin.Metadata;
import kotlin.Pair;
import kotlin.TuplesKt;
import kotlin.collections.MapsKt;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.SourceDebugExtension;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EdgeConfigProcessor {

    private final ControlPlaneResources controlPlaneResources;

    private final ObjectMapper objectMapper;
    private final Logger log = LoggerFactory.getLogger(EdgeConfigProcessor.class);
    public static final String CERTIFICATE_FILE_NAME = "/etc/aws-iot-fleetwise/VEHICLE_ID/cert.crt";
    public static final String PRIVATE_KEY_FILE_NAME = "/etc/aws-iot-fleetwise/VEHICLE_ID/pri.key";

    public EdgeConfigProcessor( ControlPlaneResources controlPlaneResources,  ObjectMapper objectMapper) {
        this.controlPlaneResources = controlPlaneResources;
        this.objectMapper = objectMapper;
    }


    public Map<String, String> setMqttConnectionParameter( Map<String, String> vehicleIDToConfigMap,
                                                           String iotCoreDeviceDataEndPoint) {
        Map<String, String> map = new HashMap<>();

        vehicleIDToConfigMap.forEach((key, value) -> {
            MqttConnectionConfig mqttConnectionConfig = new MqttConnectionConfig(
                    "iotCore",
                    iotCoreDeviceDataEndPoint,
                    key,
                    controlPlaneResources.getPolicyTopic(key),
                    controlPlaneResources.getDecoderManifestTopic(key),
                    controlPlaneResources.getSignalsTopic(key),
                    controlPlaneResources.getCheckinTopic(key),
                    CERTIFICATE_FILE_NAME.replace("VEHICLE_ID", key),
                    PRIVATE_KEY_FILE_NAME.replace("VEHICLE_ID", key)
            );
            Config inputConfig = this.parseInputConfig(key, value);


            StaticConfig staticConfig =inputConfig.getStaticConfig();
            staticConfig.setMqttConnectionConfig(mqttConnectionConfig);
            inputConfig.setStaticConfig(staticConfig);
            //StaticConfig staticConfig = inputConfig.getStaticConfig()
           // Config outputConfig = inputConfig.copy(inputConfig.getVersion(),inputConfig.getNetworkInterfaces(), staticConfig);
            String outputConfigStr = "";
            try {
                outputConfigStr = objectMapper.writeValueAsString(inputConfig);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            map.put(key, outputConfigStr);
        });
       return map;
    }


    public Map<String, String>  setCredentialsProviderParameter( Map<String,
                                                                 String> vehicleIDToConfigMap,
                                                                 String roleAliasName,
                                                                 String credentialsProviderEndpoint) {
        Map<String, String> map = new HashMap<>();
        vehicleIDToConfigMap.forEach((key, value) -> {
            CredentialsProvider credentialsProvider = new CredentialsProvider(credentialsProviderEndpoint, roleAliasName);
            S3Upload s3Upload = new S3Upload(104857600, 5242880, 10);

            Config inputConfig = parseInputConfig(key, value);
            StaticConfig staticConfig = inputConfig.getStaticConfig();
            staticConfig.setCredentialsProvider(credentialsProvider);
            staticConfig.setS3Upload(s3Upload);
            inputConfig.setStaticConfig(staticConfig);

            String outputConfigStr = "";
            try {
                outputConfigStr = objectMapper.writeValueAsString(inputConfig);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            map.put(key, outputConfigStr);
        });
        return map;
    }

    private Config parseInputConfig(String vehicleName, String json) {
        try {
            return objectMapper.readValue(json, Config.class);
        } catch (Exception ex) {
            log.error("Config File contains unknown parameter for {}. Config file: {}", vehicleName, json);
            throw new RuntimeException(ex);
        }
    }
}