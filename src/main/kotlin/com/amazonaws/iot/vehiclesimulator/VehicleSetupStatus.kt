package com.amazonaws.iot.fleetwise.vehiclesimulator

data class VehicleSetupStatus(
    val successList: Set<String>,
    val failedList: Set<String>
)
