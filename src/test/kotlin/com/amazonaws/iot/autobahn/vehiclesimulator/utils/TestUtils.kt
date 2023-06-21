package com.amazonaws.iot.autobahn.vehiclesimulator.utils

import com.amazonaws.iot.autobahn.vehiclesimulator.DeviceCertificateConfig
import com.amazonaws.iot.autobahn.vehiclesimulator.S3
import com.amazonaws.iot.autobahn.vehiclesimulator.SimulationMetaData

object TestUtils {

    const val s3Bucket = "s3-bucket"
    const val s3Key = "s3-key"
    const val commonName = "vehicle-1"
    const val countryCode = "US"
    const val organization = "AWS"
    const val caArn = "arn:aws:acm-pca:us-east-1:123456789012:certificate-authority/12345678-1234-1234-1234-123456789012"
    const val expirationDays = 120L

    /**
     * Creates mock simulation input for the given number of vehicles. Each vehicle will have a unique
     * vehicleId with prefix "vehicle-" and suffix being a positive integer
     */
    fun createSimulationInput(numVehicles: Int, provisionThing: Boolean, vehiclePrefix: String = "vehicle"): List<SimulationMetaData> {
        return (1..numVehicles).map { "$vehiclePrefix-$it" }
            .map { vehicleId ->
                val deviceCertificateConfig = if (!provisionThing) {
                    DeviceCertificateConfig(
                        caArn,
                        commonName,
                        organization,
                        countryCode,
                        expirationDays
                    )
                } else {
                    null
                }
                SimulationMetaData(
                    vehicleId,
                    S3(s3Bucket, s3Key),
                    provisionThing,
                    deviceCertificateConfig
                )
            }
    }
}
