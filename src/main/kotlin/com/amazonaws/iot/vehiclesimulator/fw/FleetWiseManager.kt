package com.amazonaws.iot.fleetwise.vehiclesimulator.fw

import com.amazonaws.iot.fleetwise.vehiclesimulator.SimulationMetaData
import com.amazonaws.iot.fleetwise.vehiclesimulator.VehicleSetupStatus
import com.amazonaws.iot.fleetwise.vehiclesimulator.exceptions.CertificateDeletionException
import com.amazonaws.iot.fleetwise.vehiclesimulator.storage.S3Storage
import com.github.michaelbull.retry.policy.decorrelatedJitterBackoff
import com.github.michaelbull.retry.policy.limitAttempts
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.policy.retryIf
import com.github.michaelbull.retry.retry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.EntityAlreadyExistsException
import software.amazon.awssdk.services.iotfleetwise.IoTFleetWiseAsyncClient
import software.amazon.awssdk.services.iot.model.ResourceAlreadyExistsException

class FleetWiseManager(private var fwClient: IoTFleetWiseAsyncClient) {
    private val log = LoggerFactory.getLogger(FleetWiseManager::class.java)

    public suspend fun createVehicle(vehicleName: String, vehicleModel: String, vehicleDecoder: String) {
        try {
            log.info("Creating Vehicle $vehicleName")
            // If this call is made multiple times using the same thing name and configuration, the call will succeed.
            // If this call is made with the same thing name but different configuration a ResourceAlreadyExistsException is thrown.
            fwClient.createVehicle { builder ->
                builder.associationBehavior("ValidateIotThingExists")
                builder.vehicleName(vehicleName)
                builder.modelManifestArn(vehicleModel)
                builder.decoderManifestArn(vehicleDecoder)
            }.await()
        } catch (ex: ResourceAlreadyExistsException) {
            log.info("Attempting to create existing Thing $vehicleName with different config, delete and re-create Thing")
            /*if (deleteVehicle(vehicleName) != null) {
                fwClient.createVehicle { builder ->
                    builder.vehicleName(vehicleName)
                    builder.modelManifestArn("arn:aws:iotfleetwise:us-east-1:195026230833:model-manifest/KII-AWS")
                    builder.decoderManifestArn("arn:aws:iotfleetwise:us-east-1:195026230833:decoder-manifest/KII-AWS")
                }.await()*/
            //} else {
                // if deleteThing returns null, the deletion failed
                // We will reuse the thing instead of creating a new one
                log.error("Attempted to re-create thing $vehicleName but failed to delete it, reuse")
            //}
        }
    }
}