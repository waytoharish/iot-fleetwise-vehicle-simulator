package com.amazonaws.iot.fleetwise.vehiclesimulator.exceptions

class EcsTaskManagerException : Exception {
    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)

    // This map contains already created vehicle IDs and corresponding ECS Task IDs
    private var alreadyCreatedVehicleMap: Map<String, String> = mapOf()
    constructor(message: String, vehicleMap: Map<String, String>) : super(message) {
        alreadyCreatedVehicleMap = vehicleMap
    }
    fun getAlreadyCreatedVehicleMap(): Map<String, String> {
        return alreadyCreatedVehicleMap
    }
}
