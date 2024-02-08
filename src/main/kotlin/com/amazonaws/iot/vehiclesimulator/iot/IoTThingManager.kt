package com.amazonaws.iot.fleetwise.vehiclesimulator.iot

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
import software.amazon.awssdk.services.iot.IotAsyncClient
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateResponse
import software.amazon.awssdk.services.iot.model.DeleteConflictException
import software.amazon.awssdk.services.iot.model.InvalidRequestException
import software.amazon.awssdk.services.iot.model.ResourceAlreadyExistsException
import software.amazon.awssdk.services.iot.model.ResourceNotFoundException
import software.amazon.awssdk.services.iot.model.ServiceUnavailableException
import software.amazon.awssdk.services.iot.model.ThrottlingException
import java.time.Duration

class IoTThingManager(private var iotClient: IotAsyncClient, private val s3Storage: S3Storage, private var iamClient: IamClient) {
    private val log = LoggerFactory.getLogger(IoTThingManager::class.java)
    private suspend fun createKeysAndCertificate(): CreateKeysAndCertificateResponse {
        log.info("Creating certificate")
        // create private / public keys and cert
        val cert =
            iotClient.createKeysAndCertificate { builder ->
                builder.setAsActive(true)
            }.await()
        return cert
    }

    private fun createRoleAndAlias(
        iamRoleName: String,
        assumeRolePolicyDocument: String,
        policyDocument: String,
        roleAlias: String,
    ): String {
        log.info("Creating Iam role '$iamRoleName' and IoT role alias for that role '$roleAlias'")
        val iamRoleArn = try {
            iamClient.createRole { builder ->
                builder.roleName(iamRoleName).assumeRolePolicyDocument(assumeRolePolicyDocument)
            }.role().arn()
        } catch (ex: EntityAlreadyExistsException) {
            log.info("Role '$iamRoleName' already exists, keeping it")
            iamClient.getRole { builder ->
                builder.roleName(iamRoleName)
            }.role().arn()
        }
        iamClient.putRolePolicy() {
            builder ->
            builder.roleName(iamRoleName).policyName(iamRoleName).policyDocument(policyDocument)
        }
        val roleAliasArn = try {
            iotClient.createRoleAlias { builder ->
                builder.roleArn(iamRoleArn).credentialDurationSeconds(3600).roleAlias(roleAlias)
            }.join().roleAliasArn()
        } catch (ex: ResourceAlreadyExistsException) {
            log.info("Role alias '$roleAlias' already exists, keeping it")
            iotClient.describeRoleAlias { builder ->
                builder.roleAlias(roleAlias)
            }.join().roleAliasDescription().roleAliasArn()
        }
        return roleAliasArn
    }

    private suspend fun createPolicyAndAttachToCert(
        policyName: String,
        policyDocument: String,
        certArn: String,
        recreatePolicyIfAlreadyExists: Boolean
    ) {
        // create policy
        try {
            log.info("Creating Policy $policyName")
            iotClient.createPolicy { builder ->
                builder.policyName(policyName).policyDocument(policyDocument)
            }.await()
        } catch (ex: ResourceAlreadyExistsException) {
            if (recreatePolicyIfAlreadyExists) {
                log.info("Policy already exists, delete and create")
                deletePolicy(policyName)
                iotClient.createPolicy { builder ->
                    builder.policyName(policyName).policyDocument(policyDocument)
                }.await()
            } else {
                log.info("Policy already exists, reusing the same policy")
            }
        }
        log.info("Attaching policy $policyName to Cert $certArn")
        iotClient.attachPolicy { builder ->
            builder.policyName(policyName)
                .target(certArn)
        }.await()
    }

    private suspend fun createThingAndAttachCert(thingName: String, certArn: String) {
        try {
            log.info("Creating Thing $thingName")
            // If this call is made multiple times using the same thing name and configuration, the call will succeed.
            // If this call is made with the same thing name but different configuration a ResourceAlreadyExistsException is thrown.
            iotClient.createThing { builder ->
                builder.thingName(thingName)
            }.await()
        } catch (ex: ResourceAlreadyExistsException) {
            log.info("Attempting to create existing Thing $thingName with different config, delete and re-create Thing")
            if (deleteThing(thingName) != null) {
                iotClient.createThing { builder ->
                    builder.thingName(thingName)
                }.await()
            } else {
                // if deleteThing returns null, the deletion failed
                // We will reuse the thing instead of creating a new one
                log.error("Attempted to re-create thing $thingName but failed to delete it, reuse")
            }
        }
        log.info("Attaching cert $certArn to Thing $thingName")
        iotClient.attachThingPrincipal { builder ->
            builder.thingName(thingName)
                .principal(certArn)
        }.await()
    }

    // If policy no longer exit, this function will throw ResourceNotFoundException
    private suspend fun deletePolicy(policyName: String) = coroutineScope {
        // To delete policy, we need to detach it from the targets (certificate)
        var nextMarker: String? 
        do {
            val response = iotClient.listTargetsForPolicy { builder ->
                builder.policyName(policyName).build()
            }.await()
            response.targets().map {
                log.info("Detaching cert $it")
                iotClient.detachPolicy { builder ->
                    builder.policyName(policyName).target(it).build()
                }.asDeferred()
            }.awaitAll()
            nextMarker = response.nextMarker()
        } while (nextMarker != null)
        // Because of the distributed nature of Amazon Web Services, it can take up to
        // five minutes after a policy is detached before it's ready to be deleted.
        // We need retry if this happened
        retry(
            retryIf<Throwable> { reason is DeleteConflictException } +
                limitAttempts(10) +
                decorrelatedJitterBackoff(
                    base = Duration.ofSeconds(10).toMillis(),
                    max = Duration.ofMinutes(1).toMillis()
                )
        ) {
            log.info("Deleting policy $policyName")
            iotClient.deletePolicy { builder ->
                builder.policyName(policyName)
            }.await()
        }
    }

    private suspend fun deleteCerts(principalSet: Set<String>) = coroutineScope {
        principalSet.forEach {
            launch {
                val certId = it.substringAfterLast("/")
                log.info("De-activating cert $certId")
                iotClient.updateCertificate { builder ->
                    builder.certificateId(certId).newStatus("INACTIVE").build()
                }.await()
                // The detachThingPrincipal is async call and might take few seconds to propagate.
                // So it might happen that cert is not ready to delete. If this happens, retry in a few moment
                // if it still failed to delete, we should log it as error but continue deleting the rest of things
                try {
                    retry(
                        retryIf<Throwable> { reason is DeleteConflictException } +
                            limitAttempts(10) +
                            decorrelatedJitterBackoff(
                                base = Duration.ofSeconds(1).toMillis(),
                                max = Duration.ofSeconds(30).toMillis()
                            )
                    ) {
                        log.info("Deleting cert $certId")
                        iotClient.deleteCertificate { builder ->
                            builder.certificateId(certId).forceDelete(true).build()
                        }.await()
                    }
                } catch (ex: DeleteConflictException) {
                    throw CertificateDeletionException("Failed to delete Cert $certId due to DeleteConflictException, ${ex.message}")
                }
            }
        }
    }

    private suspend fun deleteThing(thingName: String): List<String>? = coroutineScope {
        // First we query a list of thing principals for this Thing.
        var principalList = mutableListOf<String>()
        var nextToken: String? = null
        do {
            try {
                val response = iotClient.listThingPrincipals { builder ->
                    builder.thingName(thingName).nextToken(nextToken).build()
                }.await()
                principalList.addAll(response.principals())
                nextToken = response.nextToken()
            } catch (ex: ResourceNotFoundException) {
                // If exception raised due to Thing no longer exist, we catch the exception and continue deleting other things
                log.warn("Thing $thingName does not exist: ${ex.awsErrorDetails().errorMessage()}")
                // Set the nextToken as null to exit the loop
                nextToken = null
            }
        } while (nextToken != null)
        principalList.map {
            log.info("Detaching cert $it from Thing $thingName")
            // Before thing is ready to delete, it needs to be detached from principal
            iotClient.detachThingPrincipal { builder ->
                builder.thingName(thingName).principal(it).build()
            }.asDeferred()
        }.awaitAll()
        // Based on API doc, if thing doesn't exist, deleteThing still returns true
        // The previous call of detachThingPrincipal might take several seconds to propagate. Hence retry
        try {
            retry(
                retryIf<Throwable> { reason is InvalidRequestException } +
                    limitAttempts(10) +
                    decorrelatedJitterBackoff(
                        base = Duration.ofSeconds(1).toMillis(),
                        max = Duration.ofSeconds(30).toMillis()
                    )
            ) {
                log.info("Deleting Thing $thingName")
                iotClient.deleteThing { builder ->
                    builder.thingName(thingName)
                }.await()
            }
        } catch (ex: InvalidRequestException) {
            log.error("Failed to delete Thing $thingName after retries")
            return@coroutineScope null
        }
        return@coroutineScope principalList
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
     * @param edgeUploadS3BucketName a bucket name  to create IAM role and IoT role alias to enable Edge access
     *    to that bucket. If empty no roles and role alias will be created. These are necessary for rich-data to upload
     *    Ion files from edge directly to S3
     * @return a list of created Things and a list of failed to create things
     */
    suspend fun createAndStoreThings(
        simConfigMap: List<SimulationMetaData>, // Map<String, S3Storage.Companion.BucketAndKey>,
        policyName: String = DEFAULT_POLICY_NAME,
        policyDocument: String = DEFAULT_RICH_DATA_POLICY_DOCUMENT,
        recreatePolicyIfAlreadyExists: Boolean = false,
        edgeUploadS3BucketName: String = ""
    ): VehicleSetupStatus = coroutineScope {
        val cert = createKeysAndCertificate()
        log.info("Certificate ${cert.certificateId()} created")
        if (!edgeUploadS3BucketName.isNullOrEmpty()) {
            createRoleAndAlias(DEFAULT_RICH_DATA_ROLE_NAME, DEFAULT_RICH_DATA_IAM_ASSUME_ROLE_POLICY_DOCUMENT, getIamPolicy(edgeUploadS3BucketName), DEFAULT_RICH_DATA_ROLE_ALIAS)
        }
        createPolicyAndAttachToCert(policyName, policyDocument, cert.certificateArn(), recreatePolicyIfAlreadyExists)
        val createdThings =
            simConfigMap.chunked(CREATE_DELETE_THINGS_BATCH_SIZE).flatMap {
                // Create Things with batch size set as CREATE_DELETE_THINGS_BATCH_SIZE
                it.map {
                    async {
                        // We might run into throttling from IoT Core, hence use retry if throttle
                        retry(retryPolicyForThrottlingOrServiceUnavailable) {
                            createThingAndAttachCert(it.vehicleId, cert.certificateArn())
                        }
                        // The thing is created, store cert and private key onto S3
                        s3Storage.put(it.s3.bucket, "${it.s3.key}/cert.crt", cert.certificatePem().toByteArray())
                        s3Storage.put(it.s3.bucket, "${it.s3.key}/pri.key", cert.keyPair().privateKey().toByteArray())
                        log.info("Cert and Private Key will be stored at s3 bucket ${it.s3.bucket} key: ${it.s3.key}")
                        it.vehicleId
                    }
                }.awaitAll()
            }
        return@coroutineScope VehicleSetupStatus(createdThings.toSet(), simConfigMap.map { it.vehicleId }.toSet() - createdThings.toSet())
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
     * @throws CertificateDeletionException if certificate deletion failed
     */
    suspend fun deleteThings(
        simConfigMap: List<SimulationMetaData>, // Map<String, S3Storage.Companion.BucketAndKey>,
        policyName: String = DEFAULT_POLICY_NAME,
        deletePolicy: Boolean = false,
        deleteCert: Boolean = false,
    ): VehicleSetupStatus = coroutineScope {
        val deleteThingResponse =
            simConfigMap.chunked(CREATE_DELETE_THINGS_BATCH_SIZE).flatMap { chunkedList ->
                // Delete Things with batch size set as CREATE_DELETE_THINGS_BATCH_SIZE
                chunkedList.map {
                    async {
                        // We might run into throttling from IoT Core, hence use retry if throttle
                        retry(retryPolicyForThrottlingOrServiceUnavailable) {
                            val principalList = deleteThing(it.vehicleId)
                            if (principalList == null) {
                                null
                            } else {
                                Pair(it.vehicleId, principalList)
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }.toMap()
        // To delete certs and keys, we first group by bucket and perform batch deletion per bucket
        simConfigMap.groupBy { it.s3.bucket }.mapValues {
            it ->
            it.value.flatMap {
                listOf("${it.vehicleId}/$CERTIFICATE_FILE_NAME", "${it.vehicleId}/$PRIVATE_KEY_FILE_NAME")
            }
        }.forEach {
            s3Storage.deleteObjects(it.key, it.value)
        }
        // If raise exception policy not found, we should log it as Error but continue the cleanup
        if (deletePolicy) {
            try {
                deletePolicy(policyName)
            } catch (ex: ResourceNotFoundException) {
                // This raise exception could be thrown if the Policy no longer exist.
                // We should log it as Error but should not re-throw the exception as we cannot delete a non-existed policy
                log.error("Policy \"$policyName\" not found during deletion attempt")
            }
        }
        if (deleteCert) {
            deleteCerts(deleteThingResponse.values.flatten().toSet())
        }
        val deletedThings = deleteThingResponse.keys
        return@coroutineScope VehicleSetupStatus(deletedThings, simConfigMap.map { it.vehicleId }.toSet() - deletedThings.toSet())
    }

    /**
     * This function invokes IoTClient to return IoT Core Device Data End Point
     *
     * @return IoT Device Data End Point address
     */
    suspend fun getIoTCoreDataEndPoint(endpointType: String = "iot:Data-ATS"): String {
        return iotClient.describeEndpoint { builder ->
            builder.endpointType(endpointType)
        }.await().endpointAddress()
    }

    companion object {

        // IoT Core by default limit the createThing and deleteThing to 10TPS, hence we create / delete things
        // in a batch size of 10
        const val CREATE_DELETE_THINGS_BATCH_SIZE = 10

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

        const val DEFAULT_RICH_DATA_POLICY_DOCUMENT =
            """{
              "Version": "2012-10-17",
              "Statement": [
                {
                  "Effect": "Allow",
                  "Action": [
                    "iot:Connect",
                    "iot:Subscribe",
                    "iot:Publish",
                    "iot:Receive",
                    "iot:AssumeRoleWithCertificate"
                  ],
                  "Resource": [
                    "*"
                  ]
                }
              ]
            }"""

        const val DEFAULT_RICH_DATA_ROLE_NAME = "vehicle-simulator-credentials-provider-s3"

        const val DEFAULT_RICH_DATA_IAM_ASSUME_ROLE_POLICY_DOCUMENT =
            """{
              "Version": "2012-10-17",
              "Statement": [
                {
                  "Effect": "Allow",
                  "Principal": {
                    "Service" : "credentials.iot.amazonaws.com"
                  },
                  "Action": "sts:AssumeRole"
                }
              ]
            }"""

        const val DEFAULT_RICH_DATA_ROLE_ALIAS = "vehicle-simulator-credentials-provider"

        fun getIamPolicy(bucketName: String): String {
            return """{
              "Version": "2012-10-17",
              "Statement": [
                {
                  "Effect": "Allow",
                  "Action": [
                    "s3:PutObject",
                    "s3:ListBucket"
                  ],
                  "Resource": [
                    "arn:aws:s3:::$bucketName",
                    "arn:aws:s3:::$bucketName/*"
                  ]
                },
                {
                  "Effect": "Allow",
                  "Action": [
                    "kms:GenerateDataKey"
                  ],
                  "Resource": [
                    "*"
                  ]
                }
              ]
            }"""
        }

        private val retryPolicyForThrottlingOrServiceUnavailable =
            retryIf<Throwable> { reason is ThrottlingException || reason is ServiceUnavailableException } +
                limitAttempts(5) +
                decorrelatedJitterBackoff(
                    base = Duration.ofMillis(500).toMillis(),
                    max = Duration.ofSeconds(10).toMillis()
                )
    }
}
