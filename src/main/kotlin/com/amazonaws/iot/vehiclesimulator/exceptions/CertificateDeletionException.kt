package com.amazonaws.iot.fleetwise.vehiclesimulator.exceptions

class CertificateDeletionException : Exception {
    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
