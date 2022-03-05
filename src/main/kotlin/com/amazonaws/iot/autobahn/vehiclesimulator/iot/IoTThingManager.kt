package com.amazonaws.iot.autobahn.vehiclesimulator.iot

import com.amazonaws.iot.autobahn.vehiclesimulator.storage.S3Storage
import com.github.michaelbull.retry.policy.decorrelatedJitterBackoff
import com.github.michaelbull.retry.policy.limitAttempts
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.policy.retryIf
import com.github.michaelbull.retry.retry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.iot.IotAsyncClient
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateResponse
import software.amazon.awssdk.services.iot.model.DeleteConflictException
import software.amazon.awssdk.services.iot.model.InvalidRequestException
import software.amazon.awssdk.services.iot.model.ResourceAlreadyExistsException
import software.amazon.awssdk.services.iot.model.ResourceNotFoundException
import software.amazon.awssdk.services.iot.model.ServiceUnavailableException
import software.amazon.awssdk.services.iot.model.ThrottlingException
import java.time.Duration

class IoTThingManager(private var iotClient: IotAsyncClient, private val s3Storage: S3Storage) {
    private val log = LoggerFactory.getLogger(IoTThingManager::class.java)
    private suspend fun createKeysAndCertificate(): CreateKeysAndCertificateResponse {
        // create private / public keys and cert
        val cert =
            iotClient.createKeysAndCertificate { builder ->
                builder.setAsActive(true)
            }.asDeferred().await()
        return cert
    }

    private suspend fun createPolicyAndAttachToCert(
        policyName: String,
        policyDocument: String,
        certArn: String,
        recreatePolicyIfAlreadyExists: Boolean
    ) {
        // create policy
        try {
            iotClient.createPolicy { builder ->
                builder.policyName(policyName).policyDocument(policyDocument)
            }.asDeferred().await()
        } catch (ex: ResourceAlreadyExistsException) {
            if (recreatePolicyIfAlreadyExists) {
                log.info("Policy already exists, delete and create")
                deletePolicy(policyName)
                iotClient.createPolicy { builder ->
                    builder.policyName(policyName).policyDocument(policyDocument)
                }.asDeferred().await()
            } else {
                log.info("Policy already exists, reuse")
            }
        }
        iotClient.attachPolicy { builder ->
            builder.policyName(policyName)
                .target(certArn)
        }.asDeferred().await()
    }

    private suspend fun createThingAndAttachCert(vehicleId: String, certArn: String) {
        try {
            iotClient.createThing { builder ->
                builder.thingName(vehicleId)
            }.asDeferred().await()
        } catch (ex: ResourceAlreadyExistsException) {
            log.info("IoT Thing already exists, delete and re-create a new one")
            deleteThing(vehicleId)
            iotClient.createThing { builder ->
                builder.thingName(vehicleId)
            }.asDeferred().await()
        }
        iotClient.attachThingPrincipal { builder ->
            builder.thingName(vehicleId)
                .principal(certArn)
        }.asDeferred().await()
    }

    // If policy no longer exit, this function will throw ResourceNotFoundException
    private suspend fun deletePolicy(policyName: String) = coroutineScope {
        // To delete policy, we need to detach it from the targets (certificate)
        iotClient.listTargetsForPolicy { builder ->
            builder.policyName(policyName).build()
        }.asDeferred().await().targets().map {
            async {
                iotClient.detachPolicy { builder ->
                    builder.policyName(policyName).target(it).build()
                }
            }
        }.awaitAll()
        // Because of the distributed nature of Amazon Web Services, it can take up to
        // five minutes after a policy is detached before it's ready to be deleted.
        // We need retry if this happened
        retry(retryPolicyForDeletingPolicy) {
            log.info("deleting policy $policyName")
            iotClient.deletePolicy { builder ->
                builder.policyName(policyName)
            }.asDeferred().await()
        }
    }

    private suspend fun deleteCerts(principalSet: Set<String>) = coroutineScope {
        principalSet.forEach {
            launch {
                val certId = it.substringAfterLast("/")
                iotClient.updateCertificate { builder ->
                    builder.certificateId(certId).newStatus("INACTIVE").build()
                }.asDeferred().await()
                // The detachThingPrincipal is async call and might take few seconds to propagate.
                // So it might happen that cert is not ready to delete. If this happens, retry in a few moment
                // if it still failed to delete, we should log it as error but continue cleaning up rest of things
                try {
                    retry(retryPolicyForDeletingCert) {
                        log.info("deleting cert $certId")
                        iotClient.deleteCertificate { builder ->
                            builder.certificateId(certId).forceDelete(true).build()
                        }.asDeferred().await()
                    }
                } catch (ex: DeleteConflictException) {
                    log.error("Fail to delete Cert $certId due to DeleteConflictException")
                }
            }
        }
    }

    private suspend fun deleteThing(vehicleId: String): List<String> {
        // First we query a list of thing principals for this Thing.
        val principalList = try {
            iotClient.listThingPrincipals { builder ->
                builder.thingName(vehicleId).build()
            }.asDeferred().await().principals()
        } catch (ex: ResourceNotFoundException) {
            // If exception raised due to Thing no longer exist, we shall continue the clean-up
            log.warn("listThingPrincipals raised exception: ${ex.awsErrorDetails().errorMessage()}")
            listOf<String>()
        }
        principalList.map {
            // The sequence below is the fixed order of preparation work before deleting thing and policy
            iotClient.detachThingPrincipal { builder ->
                builder.thingName(vehicleId).principal(it).build()
            }.asDeferred().await()
        }
        // Based on API doc, if thing doesn't exist, deleteThing still returns true
        // The previous call of detachThingPrincipal might take several seconds to propagate. Hence retry
        try {
            retry(retryPolicyForDeletingThing) {
                iotClient.deleteThing { builder ->
                    builder.thingName(vehicleId)
                }.asDeferred().await()
            }
        } catch (ex: InvalidRequestException) {
            // If we still couldn't delete things after retry, throw an error
            log.error("Fail to delete Thing $vehicleId due to InvalidRequestException")
            throw ex
        }
        return principalList
    }

    /**
     * This function accepts a map of vehicle id to vehicle simulation local folder.
     * It will invoke IoTClient to create thing for each vehicle and store the certificate, private key at
     * the provided S3 path
     *
     * @param simConfigMap Mapping between vehicle id and its local simulation package folder
     * @param policyName Optional to allow client specify customized policy name
     * @param policyDocument Optional to allow client specify IoT Policy Document
     * @param recreatePolicyIfAlreadyExists a flag to indicate whether reuse or recreate policy if policy already exists
     * @return a list of created Things and a list of failed to create things
     */
    suspend fun createAndStoreThings(
        simConfigMap: Map<String, S3Storage.Companion.BucketAndKey>,
        policyName: String = DEFAULT_POLICY_NAME,
        policyDocument: String = DEFAULT_POLICY_DOCUMENT,
        recreatePolicyIfAlreadyExists: Boolean = false
    ): ThingOperationStatus = coroutineScope {
        val cert = createKeysAndCertificate()
        log.info("Certificate ${cert.certificateId()} created")
        createPolicyAndAttachToCert(policyName, policyDocument, cert.certificateArn(), recreatePolicyIfAlreadyExists)
        log.info("Policy $policyName created")
        val createdThings =
            simConfigMap.map {
                // Insert small delay as IoT Core limit createThing at 10TPS
                delay(100)
                async {
                    retry(retryPolicyForDeletingCreatingThing) {
                        createThingAndAttachCert(it.key, cert.certificateArn())
                    }
                    // The thing is created, store cert and private key onto S3
                    s3Storage.put(it.value.bucket, "${it.value.key}/cert.crt", cert.certificatePem().toByteArray())
                    s3Storage.put(it.value.bucket, "${it.value.key}/pri.key", cert.keyPair().privateKey().toByteArray())
                    log.info("Thing ${it.value} is created. Cert and Private Key stored at ${it.value} ")
                    it.key
                }
            }.awaitAll()
        return@coroutineScope ThingOperationStatus(createdThings, simConfigMap.keys.toList() - createdThings.toSet())
    }

    /**
     * This function accepts a map of vehicle id to vehicle simulation local folder.
     * It will invoke IoTClient to delete thing for each vehicle and delete the certificate, private key at
     * the provided S3 path
     *
     * @param simConfigMap Mapping between vehicle id and its local simulation package folder
     * @param policyName Optional to allow client specify customized policy name
     * @param deletePolicy Optional flag to indicate whether delete policy along with deleting things
     * @return a list of deleted Things and a list of failed to delete things
     */
    suspend fun deleteThings(
        simConfigMap: Map<String, S3Storage.Companion.BucketAndKey>,
        policyName: String = DEFAULT_POLICY_NAME,
        deletePolicy: Boolean = false
    ): ThingOperationStatus = coroutineScope {
        val deleteThingResponse =
            simConfigMap.map {
                // Insert small delay as IoT Core limit deleteThing at 10TPS
                delay(100)
                async {
                    retry(retryPolicyForDeletingCreatingThing) {
                        val principalList = deleteThing(it.key)
                        log.info("deleted thing ${it.key}")
                        Pair(it.key, principalList)
                    }
                }
            }.awaitAll().toMap()
        // To delete certs and keys, we first group by bucket and perform batch deletion per bucket
        simConfigMap.values.groupBy { it.bucket }.mapValues {
            it ->
            it.value.flatMap {
                listOf("${it.key}/$CERTIFICATE_FILE_NAME", "${it.key}/$PRIVATE_KEY_FILE_NAME")
            }
        }.forEach {
            s3Storage.deleteObjects(it.key, it.value)
        }
        // If raise exception policy not found, we should log it as Error but continue the cleanup
        if (deletePolicy) {
            try {
                deletePolicy(policyName)
            } catch (ex: ResourceNotFoundException) {
                // If raise exception policy not found, we should log it as Error but continue the cleanup
                log.error("Policy $policyName not found during deletion attempt")
            }
        }
        deleteCerts(deleteThingResponse.values.flatten().toSet())
        val deletedThings = deleteThingResponse.keys.toList()
        return@coroutineScope ThingOperationStatus(deletedThings, simConfigMap.keys.toList() - deletedThings.toSet())
    }

    /**
     * This function invokes IoTClient to return IoT Core Device Data End Point
     *
     * @return IoT Device Data End Point address
     */
    suspend fun getIoTCoreDataEndPoint(): String {
        return iotClient.describeEndpoint { builder ->
            builder.endpointType("iot:Data-ATS")
        }.asDeferred().await().endpointAddress()
    }

    companion object {
        data class ThingOperationStatus(
            val successList: List<String>,
            val failedList: List<String>
        )

        const val DEFAULT_POLICY_NAME = "vehicle-simulator-policy"

        const val CERTIFICATE_FILE_NAME = "cert.crt"

        const val PRIVATE_KEY_FILE_NAME = "pri.key"

        const val DEFAULT_POLICY_DOCUMENT =
            """{
              "Version": "2012-10-17",
              "Statement": [
                {
                  "Effect": "Allow",
                  "Action": [
                    "iot:Connect",
                    "iot:Subscribe",
                    "iot:Publish",
                    "iot:Receive"
                  ],
                  "Resource": [
                    "*"
                  ]
                }
              ]
            }"""

        private val retryPolicyForDeletingPolicy =
            retryIf<Throwable> { reason is DeleteConflictException } +
                limitAttempts(10) +
                decorrelatedJitterBackoff(
                    base = Duration.ofSeconds(10).toMillis(),
                    max = Duration.ofMinutes(1).toMillis()
                )

        private val retryPolicyForDeletingThing =
            retryIf<Throwable> { reason is InvalidRequestException } +
                limitAttempts(10) +
                decorrelatedJitterBackoff(
                    base = Duration.ofSeconds(1).toMillis(),
                    max = Duration.ofSeconds(30).toMillis()
                )

        private val retryPolicyForDeletingCert =
            retryIf<Throwable> { reason is DeleteConflictException } +
                limitAttempts(10) +
                decorrelatedJitterBackoff(
                    base = Duration.ofSeconds(1).toMillis(),
                    max = Duration.ofSeconds(30).toMillis()
                )

        private val retryPolicyForDeletingCreatingThing =
            retryIf<Throwable> { reason is ThrottlingException || reason is ServiceUnavailableException } +
                limitAttempts(10) +
                decorrelatedJitterBackoff(
                    base = Duration.ofSeconds(10).toMillis(),
                    max = Duration.ofSeconds(60).toMillis()
                )
    }
}
