package com.amazonaws.iot.fleetwise.vehiclesimulator.fw;

import software.amazon.awssdk.services.iotfleetwise.model.ImportSignalCatalogRequest;
import software.amazon.awssdk.services.iotfleetwise.model.ImportSignalCatalogResponse;

public interface FleetWiseVehicleModeling {
    public ImportSignalCatalogResponse createSignalCatalog(ImportSignalCatalogRequest importSignalCatalogRequest);
}
