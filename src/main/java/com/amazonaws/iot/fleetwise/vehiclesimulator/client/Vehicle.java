package com.amazonaws.iot.fleetwise.vehiclesimulator.client;


import software.amazon.awssdk.services.iotfleetwise.IoTFleetWiseClient;
import software.amazon.awssdk.services.iotfleetwise.model.CreateVehicleRequest;
import software.amazon.awssdk.services.iotfleetwise.model.CreateVehicleResponse;

import java.util.HashMap;
import java.util.Map;

public class Vehicle {
    public static void main(String[] args) {

        IoTFleetWiseClient client = IoTFleetWiseClient.builder().build();

        String modelManifestArn = "arn:aws:iotfleetwise:us-east-1:123456789012:model-manifest/MyModelManifest";
        String decoderManifestArn = "arn:aws:iotfleetwise:us-east-1:123456789012:decoder-manifest/MyDecoderManifest";
        String vehicleName = "MyVehicle";

        Map<String, String> attributes = new HashMap<>();
        attributes.put("fuelType", "electric");
        attributes.put("modelYear", "2022");


        CreateVehicleRequest requestItem = CreateVehicleRequest.builder()
                .modelManifestArn(modelManifestArn)
                .decoderManifestArn(decoderManifestArn)
                .vehicleName(vehicleName)
                .attributes(attributes)
                .associationBehavior("CreateIotThing") // or "ValidateIotThingExists"
                .build();

        CreateVehicleResponse response = client.createVehicle(requestItem);
        System.out.println("Vehicle created: " + response.vehicleName());
    }
}

