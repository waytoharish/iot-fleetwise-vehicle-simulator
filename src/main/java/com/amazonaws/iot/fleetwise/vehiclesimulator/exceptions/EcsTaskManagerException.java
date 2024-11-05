package com.amazonaws.iot.fleetwise.vehiclesimulator.exceptions;

import java.util.Map;
import kotlin.Metadata;
import kotlin.collections.MapsKt;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

public final class EcsTaskManagerException extends Exception {

    private Map<String, String>  alreadyCreatedVehicleMap;

    public EcsTaskManagerException( String message,  Throwable cause) {
        super(message, cause);
        this.alreadyCreatedVehicleMap = MapsKt.emptyMap();
    }

    public EcsTaskManagerException( String message) {
        super(message);
        this.alreadyCreatedVehicleMap = MapsKt.emptyMap();
    }

    public EcsTaskManagerException( String message,  Map<String, String>  vehicleMap) {
        super(message);
        this.alreadyCreatedVehicleMap = MapsKt.emptyMap();
        this.alreadyCreatedVehicleMap = vehicleMap;
    }


    public final Map<String, String>  getAlreadyCreatedVehicleMap() {
        return this.alreadyCreatedVehicleMap;
    }
}
