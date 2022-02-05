package com.amazonaws.iot.autobahn.vehiclesimulator.exceptions

class EcsTaskManagerException : Exception {
    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
