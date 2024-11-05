package com.amazonaws.iot.fleetwise.vehiclesimulator.util;

import com.amazonaws.iot.fleetwise.vehiclesimulator.client.SignalCatalog;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import software.amazon.awssdk.services.iotfleetwise.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class Utils {
    public static InputStream getFileFromResourceAsStream(String fileName) {
        ClassLoader classLoader = SignalCatalog.class.getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(fileName);
        // the stream holding the file content
        if (inputStream == null) {
            throw new IllegalArgumentException("file not found! " + fileName);
        } else {
            return inputStream;
        }

    }


    public static String readJsonFile(String filePath) {
        try {
            InputStream inputStream = Utils.getFileFromResourceAsStream(filePath);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON file: " + filePath, e);
        }
    }

    public static List<String> parseStringFromJson(String jsonFile) {
        String jsonContent = Utils.readJsonFile(jsonFile);
        JsonArray nodesArray = new Gson().fromJson(jsonContent, JsonObject.class).getAsJsonArray("nodes");
        return nodesArray.asList().stream()
                .map(jsonElement -> new Gson().fromJson(jsonElement, String.class))
                .collect(Collectors.toList());
    }

    public static List<NetworkInterface> parseNetworkInterfaceFromJson(String jsonFile) {
        String jsonContent = Utils.readJsonFile(jsonFile);
        JsonArray nodesArray = new Gson().fromJson(jsonContent, JsonObject.class).getAsJsonArray("networkInterfaces");
        return nodesArray.asList().stream()
                .map(jsonElement -> new Gson().fromJson(jsonElement, NetworkInterface.class))
                .collect(Collectors.toList());
    }

    public static List<SignalDecoder> parseSignalDecoderFromJson(String jsonFile) {
        String jsonContent = Utils.readJsonFile(jsonFile);
        JsonArray nodesArray = new Gson().fromJson(jsonContent, JsonObject.class).getAsJsonArray("signalDecoders");
        return nodesArray.asList().stream()
                .map(jsonElement -> new Gson().fromJson(jsonElement, SignalDecoder.class))
                .collect(Collectors.toList());
    }


    public static List<Node> parseNodesFromJson(String jsonFile) {
        String jsonContent = Utils.readJsonFile(jsonFile);
        JsonArray nodesArray = new Gson().fromJson(jsonContent, JsonObject.class).getAsJsonArray("nodes");
        return nodesArray.asList().stream()
                .map(jsonElement -> new Gson().fromJson(jsonElement, Node.class))
                .collect(Collectors.toList());
    }

    public static CollectionScheme collectionScheme(String jsonFile) {

        JsonObject collectionScheme = new Gson().fromJson(jsonFile, JsonObject.class).getAsJsonObject("collectionScheme");
        return new Gson().fromJson(collectionScheme, CollectionScheme.class);
    }

    public static String getvalue(String jsonContent, String key) {
        JsonObject convertedObject = new Gson().fromJson(jsonContent, JsonObject.class);
        return convertedObject.getAsJsonObject().get(key).getAsString();
    }

    public static List<SignalInformation> signalToCollectFromJson(String jsonFile) {

        JsonArray nodesArray = new Gson().fromJson(jsonFile, JsonObject.class).getAsJsonArray("signalsToCollect");
        return nodesArray.asList().stream()
                .map(jsonElement -> new Gson().fromJson(jsonElement, SignalInformation.class))
                .collect(Collectors.toList());
    }

    public static List<DataDestinationConfig> dataDestinationConfigFromJson(String jsonFile) {

        JsonArray nodesArray = new Gson().fromJson(jsonFile, JsonObject.class).getAsJsonArray("dataDestinationConfigs");
        return nodesArray.asList().stream()
                .map(jsonElement -> new Gson().fromJson(jsonElement, DataDestinationConfig.class))
                .collect(Collectors.toList());
    }

}
