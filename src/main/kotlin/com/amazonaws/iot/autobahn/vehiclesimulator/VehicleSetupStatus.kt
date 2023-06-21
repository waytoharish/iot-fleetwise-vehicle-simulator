package com.amazonaws.iot.autobahn.vehiclesimulator

data class VehicleSetupStatus(
    val successList: Set<String>,
    val failedList: Set<String>
)
