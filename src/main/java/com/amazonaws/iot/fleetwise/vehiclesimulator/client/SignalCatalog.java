package com.amazonaws.iot.fleetwise.vehiclesimulator.client;

import com.amazonaws.iot.fleetwise.vehiclesimulator.util.Utils;
import software.amazon.awssdk.services.iotfleetwise.IoTFleetWiseClient;
import software.amazon.awssdk.services.iotfleetwise.model.*;


import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class SignalCatalog {
    public static void main(String[] args) {
        IoTFleetWiseClient fleetWiseClient = IoTFleetWiseClient.builder().build();
        String jsonFile = "./fw/signal-catalog-nodes.json";
        //CreateSignalCatalogResponse response = createSignalCatalog(jsonFile, fleetWiseClient);


        System.out.println("Signal catalog created: " + getSignalCatalog(fleetWiseClient));
    }

    private static CreateSignalCatalogResponse createSignalCatalog(String jsonFile, IoTFleetWiseClient fleetWiseClient) {
        List<Node> nodes = Utils.parseNodesFromJson(jsonFile);
        CreateSignalCatalogRequest request = CreateSignalCatalogRequest.builder()
                .name("main-signal-catalog")
                .nodes(nodes)
                .build();

        return fleetWiseClient.createSignalCatalog(request);
    }

    private static String getSignalCatalog(IoTFleetWiseClient fleetWiseClient) {

       GetSignalCatalogRequest request = GetSignalCatalogRequest.builder()
                .name("main-signal-catalog")
                .build();

        return fleetWiseClient.getSignalCatalog(request).arn();
    }

    private static DeleteSignalCatalogResponse deleteSignalCatalog(String jsonFile, IoTFleetWiseClient fleetWiseClient) {
        DeleteSignalCatalogRequest request = DeleteSignalCatalogRequest.builder()
                .name("main-signal-catalog")
                .build();

        return fleetWiseClient.deleteSignalCatalog(request);
    }




    private static List<Branch> parseBranchFromJson(String jsonFile) {
        String jsonContent = Utils.readJsonFile(jsonFile);
        JsonArray signalsArray = new Gson().fromJson(jsonContent, JsonObject.class).getAsJsonArray("signals");
        return signalsArray.asList().stream()
                .map(jsonElement -> new Gson().fromJson(jsonElement, Branch.class))
                .collect(Collectors.toList());
    }





}


