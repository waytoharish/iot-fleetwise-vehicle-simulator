package com.amazonaws.iot.autobahn.vehiclesimulator.exceptions

class IoTThingManagerException : Exception {
    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
