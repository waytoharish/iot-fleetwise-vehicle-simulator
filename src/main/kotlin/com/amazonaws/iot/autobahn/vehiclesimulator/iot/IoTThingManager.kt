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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.services.iot.IotClient
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateResponse
import software.amazon.awssdk.services.iot.model.DeleteConflictException
import software.amazon.awssdk.services.iot.model.InvalidRequestException
import software.amazon.awssdk.services.iot.model.ResourceAlreadyExistsException
import software.amazon.awssdk.services.iot.model.ResourceNotFoundException
import software.amazon.awssdk.services.iot.model.ServiceUnavailableException
import software.amazon.awssdk.services.iot.model.ThrottlingException
import java.time.Duration

class IoTThingManager(private var client: IotClient, private val s3Storage: S3Storage) {
    private fun createKeysAndCertificate(): CreateKeysAndCertificateResponse {
        // create private / public keys and cert
        val cert =
            client.createKeysAndCertificate { builder ->
                builder.setAsActive(true)
            }
        return cert
    }

    private fun createPolicyAndAttachToCert(
        policyName: String,
        policyDocument: String,
        certArn: String,
        recreatePolicyIfAlreadyExists: Boolean
    ) {
        // create policy
        try {
            client.createPolicy { builder ->
                builder.policyName(policyName).policyDocument(policyDocument)
            }
        } catch (ex: ResourceAlreadyExistsException) {
            if (recreatePolicyIfAlreadyExists) {
                println("Policy already exists, delete and create")
                val response = client.listTargetsForPolicy { builder -> builder.policyName(policyName).build() }
                runBlocking { deletePolicy(policyName, response.targets().toSet()) }
                client.createPolicy { builder ->
                    builder.policyName(policyName).policyDocument(policyDocument)
                }
            } else {
                println("Policy already exists, reuse")
            }
        }
        client.attachPolicy { builder ->
            builder.policyName(policyName)
                .target(certArn)
        }
    }

    private suspend fun createThingAndAttachCert(vehicleId: String, certArn: String) {
        try {
            client.createThing { builder ->
                builder.thingName(vehicleId)
            }
        } catch (ex: ResourceAlreadyExistsException) {
            println("IoT Thing already exists, delete and re-create a new one")
            deleteThing(vehicleId)
            client.createThing { builder ->
                builder.thingName(vehicleId)
            }
        }
        client.attachThingPrincipal { builder ->
            builder.thingName(vehicleId)
                .principal(certArn)
        }
    }

    private suspend fun deletePolicy(policyName: String, principalSet: Set<String>) = coroutineScope {
        principalSet.forEach {
            launch {
                client.detachPolicy { builder ->
                    builder.policyName(policyName).target(it).build()
                }
            }
        }
        // Because of the distributed nature of Amazon Web Services, it can take up to
        // five minutes after a policy is detached before it's ready to be deleted.
        // We need retry if this happened
        retry(retryPolicyForDeletingPolicy) {
            try {
                client.deletePolicy { builder ->
                    builder.policyName(policyName)
                }
            } catch (ex: ResourceNotFoundException) {
                // If raise exception policy not found, we should log it as Error but continue the clean up
                println("Policy $policyName not found during deletion attempt")
            }
        }
    }

    private suspend fun deleteCerts(principalSet: Set<String>) = coroutineScope {
        principalSet.forEach {
            launch {
                client.updateCertificate { builder ->
                    builder.certificateId(it.substringAfterLast("/")).newStatus("INACTIVE").build()
                }
                // The detachThingPrincipal is async call and might take few seconds to propagate.
                // So it might happen that cert is not ready to delete. If this happens, retry in a few moment
                // if it still failed to delete, we should log it as error but continue cleaning up rest of things
                try {
                    retry(retryPolicyForDeletingCert) {
                        client.deleteCertificate { builder ->
                            builder.certificateId(it.substringAfterLast("/")).forceDelete(true).build()
                        }
                    }
                } catch (ex: DeleteConflictException) {
                    // TODO Log Error
                    println("Fail to delete Cert ${it.substringAfterLast("/")} due to DeleteConflictException")
                }
            }
        }
    }

    private suspend fun deleteThing(vehicleId: String): List<String> {
        // First we query a list of thing principals for this Thing.
        val principalList = try {
            client.listThingPrincipals { builder ->
                builder.thingName(vehicleId).build()
            }.principals()
        } catch (ex: ResourceNotFoundException) {
            // If exception raised due to Thing no longer exist, we shall continue the operation
            // TODO Log Warning
            println("listThingPrincipals raised exception: ${ex.awsErrorDetails().errorMessage()}")
            listOf<String>()
        }
        principalList.map {
            // The sequence below is the fixed order of preparation work before deleting thing and policy
            client.detachThingPrincipal { builder ->
                builder.thingName(vehicleId).principal(it).build()
            }
        }
        // Based on API doc, if thing doesn't exist, deleteThing still returns true
        // The previous call of detachThingPrincipal might take several seconds to propagate. Hence retry
        try {
            retry(retryPolicyForDeletingThing) {
                client.deleteThing { builder ->
                    builder.thingName(vehicleId)
                }
            }
        } catch (ex: InvalidRequestException) {
            // If we still couldn't delete things after retry, throw an error
            // TODO Log ERROR
            println("Fail to delete Thing $vehicleId due to InvalidRequestException")
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
    fun createAndStoreThings(
        simConfigMap: Map<String, S3Storage.Companion.BucketAndKey>,
        policyName: String = DEFAULT_POLICY_NAME,
        policyDocument: String = DEFAULT_POLICY_DOCUMENT,
        recreatePolicyIfAlreadyExists: Boolean = false
    ): ThingOperationStatus {
        val cert = createKeysAndCertificate()
        println("Certificate ${cert.certificateId()} created")
        createPolicyAndAttachToCert(policyName, policyDocument, cert.certificateArn(), recreatePolicyIfAlreadyExists)
        println("Policy $policyName created")
        val createdThings = runBlocking {
            simConfigMap.map {
                async {
                    retry(retryPolicyForDeletingCreatingThing) {
                        createThingAndAttachCert(it.key, cert.certificateArn())
                    }
                    // The thing is created, store cert and private key onto S3
                    s3Storage.put(it.value.bucket, "${it.value.key}/cert.crt", cert.certificatePem().toByteArray())
                    s3Storage.put(it.value.bucket, "${it.value.key}/pri.key", cert.keyPair().privateKey().toByteArray())
                    println("Thing ${it.value} is created. Cert and Private Key stored at ${it.value} ")
                    it.key
                }
            }.awaitAll()
        }
        return ThingOperationStatus(createdThings, simConfigMap.keys.toList() - createdThings.toSet())
    }

    /**
     * This function accepts a map of vehicle id to vehicle simulation local folder.
     * It will invoke IoTClient to delete thing for each vehicle and delete the certificate, private key at
     * the provided S3 path
     *
     * @param simConfigMap Mapping between vehicle id and its local simulation package folder
     * @param policyName Optional to allow client specify customized policy name
     * @return a list of deleted Things and a list of failed to delete things
     */
    fun deleteThings(
        simConfigMap: Map<String, S3Storage.Companion.BucketAndKey>,
        policyName: String = DEFAULT_POLICY_NAME,
    ): ThingOperationStatus {
        val deleteThingResponse = runBlocking {
            simConfigMap.map {
                async {
                    retry(retryPolicyForDeletingCreatingThing) {
                        val principalList = deleteThing(it.key)
                        println("deleted thing ${it.key}")
                        Pair(it.key, principalList)
                    }
                }
            }.awaitAll()
        }.toMap()
        // To delete certs and keys, we first group by bucket and perform batch deletion per bucket
        simConfigMap.values.groupBy { it.bucket }.mapValues {
            it ->
            it.value.flatMap {
                listOf("${it.key}/$CERTIFICATE_FILE_NAME", "${it.key}/$PRIVATE_KEY_FILE_NAME")
            }
        }.forEach {
            s3Storage.deleteObjects(it.key, it.value)
        }
        runBlocking {
            deletePolicy(policyName, deleteThingResponse.values.flatten().toSet())
            deleteCerts(deleteThingResponse.values.flatten().toSet())
        }
        val deletedThings = deleteThingResponse.keys.toList()
        return ThingOperationStatus(deletedThings, simConfigMap.keys.toList() - deletedThings.toSet())
    }

    /**
     * This function invokes IoTClient to return IoT Core Device Data End Point
     *
     * @return IoT Device Data End Point address
     */
    fun getIoTCoreDataEndPoint(): String {
        return client.describeEndpoint { builder ->
            builder.endpointType("iot:Data-ATS")
        }.endpointAddress()
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
                    "iot:*"
                  ],
                  "Resource": [
                    "*"
                  ]
                }
              ]
            }"""

        private const val MAX_RETRIES: Int = 9

        private val retryPolicyForDeletingPolicy =
            retryIf<Throwable> { reason is DeleteConflictException } +
                limitAttempts(MAX_RETRIES) +
                decorrelatedJitterBackoff(
                    base = Duration.ofSeconds(10).toMillis(),
                    max = Duration.ofMinutes(1).toMillis()
                )

        private val retryPolicyForDeletingThing =
            retryIf<Throwable> { reason is InvalidRequestException } +
                limitAttempts(MAX_RETRIES) +
                decorrelatedJitterBackoff(
                    base = Duration.ofSeconds(1).toMillis(),
                    max = Duration.ofSeconds(30).toMillis()
                )

        private val retryPolicyForDeletingCert =
            retryIf<Throwable> { reason is DeleteConflictException } +
                limitAttempts(MAX_RETRIES) +
                decorrelatedJitterBackoff(
                    base = Duration.ofSeconds(1).toMillis(),
                    max = Duration.ofSeconds(30).toMillis()
                )

        private val retryPolicyForDeletingCreatingThing =
            retryIf<Throwable> { reason is ThrottlingException || reason is ServiceUnavailableException } +
                limitAttempts(MAX_RETRIES) +
                decorrelatedJitterBackoff(
                    base = Duration.ofSeconds(1).toMillis(),
                    max = Duration.ofSeconds(10).toMillis()
                )
    }
}
