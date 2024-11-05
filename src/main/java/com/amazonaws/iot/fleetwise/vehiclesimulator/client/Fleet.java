package com.amazonaws.iot.fleetwise.vehiclesimulator.client;

import software.amazon.awssdk.services.iotfleetwise.IoTFleetWiseClient;
import software.amazon.awssdk.services.iotfleetwise.model.*;

public class Fleet {

    public static void main(String[] args) {
        IoTFleetWiseClient client = IoTFleetWiseClient.builder().build();

        CreateFleetResponse response =createFleetResponse(client);
        System.out.println("Fleet ARN: " + response.arn());
    }

    private static CreateFleetResponse createFleetResponse(IoTFleetWiseClient client) {
        CreateFleetRequest request = CreateFleetRequest.builder()
                .fleetId("myFleetId")
                .signalCatalogArn("arn:aws:iotfleetwise:us-east-1:034352053400:signal-catalog/main-signal-catalog")
                .description("My Fleet Description")
                .build();

        return client.createFleet(request);
    }

    private static DeleteFleetResponse deleteCreateFleetResponse(IoTFleetWiseClient client) {
       DeleteFleetRequest request = DeleteFleetRequest.builder()
                .fleetId("myFleetId")
                .build();

        return client.deleteFleet(request);
    }

    private static GetFleetResponse getCreateFleetResponse(IoTFleetWiseClient client) {
       GetFleetRequest request = GetFleetRequest.builder()
                .fleetId("myFleetId")
                .build();

        return client.getFleet(request);
    }


    private static  AssociateVehicleFleetResponse  associateVehicleFleet(IoTFleetWiseClient client) {
        AssociateVehicleFleetRequest request = AssociateVehicleFleetRequest.builder()
                .fleetId("myFleetId")
                .vehicleName("blog-vehicle-01")
                .build();

        return client.associateVehicleFleet(request);
    }

    private static  DisassociateVehicleFleetResponse  disAssociateVehicleFleet(IoTFleetWiseClient client) {
        DisassociateVehicleFleetRequest request = DisassociateVehicleFleetRequest.builder()
                .fleetId("myFleetId")
                .vehicleName("blog-vehicle-01")
                .build();

        return client.disassociateVehicleFleet(request);
    }








}
