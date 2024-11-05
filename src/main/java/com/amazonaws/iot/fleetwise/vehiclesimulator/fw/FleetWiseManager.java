package com.amazonaws.iot.fleetwise.vehiclesimulator.fw;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.iot.model.ResourceAlreadyExistsException;
import software.amazon.awssdk.services.iotfleetwise.IoTFleetWiseAsyncClient;
import software.amazon.awssdk.services.iotfleetwise.model.CreateVehicleRequest;
import software.amazon.awssdk.services.iotfleetwise.model.CreateVehicleResponse;

public final class FleetWiseManager {

    private final IoTFleetWiseAsyncClient fwClient;
    private final Logger log = LoggerFactory.getLogger(FleetWiseManager.class);

    public FleetWiseManager( IoTFleetWiseAsyncClient fwClient) {
        this.fwClient = fwClient;
    }

    public void createVehicle(String vehicleName, String vehicleModel, String vehicleDecoder) {
        try {
            log.info("Creating Vehicle {}", vehicleName);
            // If this call is made multiple times using the same thing name and configuration, the call will succeed.
            // If this call is made with the same thing name but different configuration a ResourceAlreadyExistsException is thrown.

            CompletableFuture<CreateVehicleResponse> createVehicleResponse =
            fwClient.createVehicle(CreateVehicleRequest.builder()
                            .associationBehavior("ValidateIotThingExists").
                  //  .associationBehavior("ValidateIotThingExists", required = true).
                    vehicleName(vehicleName).
                    modelManifestArn(vehicleModel).
                    decoderManifestArn(vehicleDecoder).build()
            );

            log.info("Vehicle created {}", createVehicleResponse.get().vehicleName());
        } catch (ResourceAlreadyExistsException ex) {
            log.info("Attempting to create existing Thing {} with different config, delete and re-create Thing", vehicleName);

            /*if (deleteVehicle(vehicleName) != null) {
                fwClient.createVehicle { builder ->
                    builder.vehicleName(vehicleName)
                    builder.modelManifestArn("arn:aws:iotfleetwise:us-east-1:195026230833:model-manifest/KII-AWS", required = true)
                    builder.decoderManifestArn("arn:aws:iotfleetwise:us-east-1:195026230833:decoder-manifest/KII-AWS", required = true)
                }.await()*/
            //} else {
            // if deleteThing returns null, the deletion failed
            // We will reuse the thing instead of creating a new one
            log.error("Attempted to re-create thing {} but failed to delete it, reuse", vehicleName);
            //}
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
