package com.amazonaws.iot.autobahn.vehiclesimulator.iot

import com.amazonaws.iot.autobahn.vehiclesimulator.storage.S3Storage
import com.github.michaelbull.retry.policy.decorrelatedJitterBackoff
import com.github.michaelbull.retry.policy.limitAttempts
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.policy.retryIf
import com.github.michaelbull.retry.retry
import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.services.iot.IotClient
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateResponse
import software.amazon.awssdk.services.iot.model.DeleteConflictException
import software.amazon.awssdk.services.iot.model.ResourceAlreadyExistsException
import software.amazon.awssdk.services.iot.model.ResourceNotFoundException
import software.amazon.awssdk.services.iot.model.ServiceUnavailableException
import software.amazon.awssdk.services.iot.model.ThrottlingException
import java.io.BufferedReader

class IoTThingManager(private var client: IotClient, private val s3Storage: S3Storage) {
    private fun createKeysAndCertificate(): CreateKeysAndCertificateResponse {
        // create private / public keys and cert
        val cert =
            client.createKeysAndCertificate { builder ->
                builder.setAsActive(true)
            }
        return cert
    }

    private fun createPolicyAndAttachToCert(policyName: String, policyDocumentPath: String, certArn: String) {
        val policyDoc = this.javaClass.classLoader
            .getResourceAsStream(policyDocumentPath)!!
            .bufferedReader()
            .use(BufferedReader::readText)
        // create policy
        try {
            client.createPolicy { builder ->
                builder.policyName(policyName).policyDocument(policyDoc)
            }
        } catch (ex: ResourceAlreadyExistsException) {
            println("Policy already exist, delete and create")
            val response = client.listTargetsForPolicy { builder -> builder.policyName(policyName).build() }
            runBlocking { deletePolicy(policyName, response.targets().toSet()) }
            client.createPolicy { builder ->
                builder.policyName(policyName).policyDocument(policyDoc)
            }
        }
        client.attachPolicy { builder ->
            builder.policyName(policyName)
                .target(certArn)
        }
    }

    private fun createThingAndAttachCert(vehicleId: String, certArn: String) {
        try {
            client.createThing { builder ->
                builder.thingName(vehicleId)
            }
        } catch (ex: ResourceAlreadyExistsException) {
            println("IoT Thing already exist, delete and re-create a new one")
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

    private suspend fun deletePolicy(policyName: String, setOfPrincipal: Set<String>) {
        setOfPrincipal.forEach {
            client.detachPolicy { builder ->
                builder.policyName(policyName).target(it).build()
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
                // If raise exception policy not found, continue the operation
                println("Policy $policyName not found")
            }
        }
    }

    private suspend fun deleteCerts(setOfPrincipal: Set<String>) {
        setOfPrincipal.forEach {
            client.updateCertificate { builder ->
                builder.certificateId(it.substringAfterLast("/")).newStatus("INACTIVE").build()
            }
            // The detachThingPrincipal is async call and might take few seconds to propagate.
            // So it might happen that cert is not ready to delete. If this happens, retry in a few moment
            retry(retryPolicyForDeletingCert) {
                client.deleteCertificate { builder ->
                    builder.certificateId(it.substringAfterLast("/")).forceDelete(true).build()
                }
            }
        }
    }

    private fun deleteThing(vehicleId: String): List<String> {
        // First we query a list of thing principals for this Thing.
        val listOfPrincipal = try {
            client.listThingPrincipals { builder ->
                builder.thingName(vehicleId).build()
            }.principals()
        } catch (ex: ResourceNotFoundException) {
            // If exception raised due to Thing no longer exist, we shall continue the operation
            // TODO Log Warning
            println("listThingPrincipals raised exception: ${ex.awsErrorDetails().errorMessage()}")
            listOf<String>()
        }
        listOfPrincipal.map {
            // The sequence below is the fixed order of preparation work before deleting thing and policy
            client.detachThingPrincipal { builder ->
                builder.thingName(vehicleId).principal(it).build()
            }
        }
        // Based on API doc, if thing doesn't exist, deleteThing still returns true
        client.deleteThing { builder ->
            builder.thingName(vehicleId)
        }
        return listOfPrincipal
    }

    /**
     * This function accepts a map of vehicle id to vehicle simulation local folder.
     * It will invoke IoTClient to create thing for each vehicle and store the certificate, private key at
     * the provided S3 path
     *
     * @param simConfigMap Mapping between vehicle id and its local simulation package folder
     * @param policyName Optional to allow client specify customized policy name
     * @param policyDocumentPath Optional to allow client specify IoT Policy
     * @return a list of created Things and a list of failed to create things
     */
    suspend fun createAndStoreThings(
        simConfigMap: Map<String, String>,
        policyName: String = "vehicle-simulator-policy",
        policyDocumentPath: String = "Policy/IoTPolicy.json"
    ): Pair<List<String>, List<String>> {
        val cert = createKeysAndCertificate()
        println("Certificate ${cert.certificateId()} created")
        createPolicyAndAttachToCert(policyName, policyDocumentPath, cert.certificateArn())
        println("Policy $policyName created")
        val createdThings = simConfigMap.map {
            retry(retryPolicyForDeletingCreatingThing) {
                createThingAndAttachCert(it.key, cert.certificateArn())
            }
            // The thing is created, store cert and private key onto S3
            s3Storage.put("${it.value}/cert.crt", cert.certificatePem().toByteArray())
            s3Storage.put("${it.value}/pri.key", cert.keyPair().privateKey().toByteArray())
            println("Thing ${it.value} is created. Cert and Private Key stored at ${it.value} ")
            it.key
        }
        return Pair(createdThings, simConfigMap.keys.toList().filterNot { createdThings.contains(it) })
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
    suspend fun deleteThings(
        simConfigMap: Map<String, String>,
        policyName: String = "vehicle-simulator-policy",
    ): Pair<List<String>, List<String>> {
        val deleteThingResponse = simConfigMap.map {
            retry(retryPolicyForDeletingCreatingThing) {
                println("deleting ${it.key}")
                val listOfPrincipal = deleteThing(it.key)
                Pair(it.key, listOfPrincipal)
            }
        }.toMap()
        s3Storage.deleteObjectsFromSameBucket(
            simConfigMap.values.map { "$it/cert.crt" }
        )
        s3Storage.deleteObjectsFromSameBucket(
            simConfigMap.values.map { "$it/pri.key" }
        )
        deletePolicy(policyName, deleteThingResponse.values.flatten().toSet())
        deleteCerts(deleteThingResponse.values.flatten().toSet())
        val deletedThings = deleteThingResponse.keys.toList()
        return Pair(deletedThings, simConfigMap.keys.toList().filterNot { deletedThings.contains(it) })
    }

    /**
     * This function invokes IoTClient to return end point
     *
     * @return IoT End Point
     */
    fun getEndPoint(): String {
        return client.describeEndpoint { builder ->
            builder.endpointType("iot:Data-ATS")
        }.endpointAddress()
    }

    companion object {
        private const val MAX_RETRIES: Int = 9

        private val retryPolicyForDeletingPolicy =
            retryIf<Throwable> { reason is DeleteConflictException } +
                limitAttempts(MAX_RETRIES) +
                decorrelatedJitterBackoff(base = 1000, max = 10 * 1000)

        private val retryPolicyForDeletingCert =
            retryIf<Throwable> { reason is DeleteConflictException } +
                limitAttempts(MAX_RETRIES) +
                decorrelatedJitterBackoff(base = 1000, max = 300 * 1000)

        private val retryPolicyForDeletingCreatingThing =
            retryIf<Throwable> { reason is ThrottlingException || reason is ServiceUnavailableException } +
                limitAttempts(MAX_RETRIES) +
                decorrelatedJitterBackoff(base = 1000, max = 10 * 1000)
    }
}
