package com.amazonaws.iot.autobahn.vehiclesimulator.iot

import com.amazonaws.iot.autobahn.vehiclesimulator.exceptions.IoTThingManagerException
import software.amazon.awssdk.services.iot.IotClient
import software.amazon.awssdk.services.iot.model.AttributePayload
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateResponse
import software.amazon.awssdk.services.iot.model.InvalidRequestException
import software.amazon.awssdk.services.iot.model.MalformedPolicyException
import software.amazon.awssdk.services.iot.model.ResourceAlreadyExistsException
import software.amazon.awssdk.services.iot.model.ResourceNotFoundException
import java.io.File

class IoTThingManager(private var client: IotClient) {

    private fun createThing(vehicleId: String): CreateKeysAndCertificateResponse {
        // First step is to create Thing
        val policyName = Companion.POLICY_PREFIX + vehicleId + Companion.POLICY_POSTFIX
        try {
            client.createThing { builder ->
                builder.thingName(vehicleId).attributePayload(
                    fun(builder: AttributePayload.Builder) {
                        builder.attributes(mapOf("type" to "kfarm_sim_vehicle"))
                    }
                )
            }
        } catch (ex: ResourceAlreadyExistsException) {
            // This exception can happen if another thing with the same name already exist
            throw IoTThingManagerException("Fail to create Thing for $vehicleId", ex)
        } catch (ex: InvalidRequestException) {
            throw IoTThingManagerException("Fail to create Thing for $vehicleId", ex)
        }
        val policy = try {
            // create policy
            client.createPolicy { builder ->
                builder.policyName(policyName).policyDocument(POLICY_DOC)
            }
        } catch (ex: ResourceAlreadyExistsException) {
            // This exception can happen if another policy with the same name already exist
            throw IoTThingManagerException("Fail to create Policy for $vehicleId", ex)
        } catch (ex: MalformedPolicyException) {
            // Policy document is not valid
            throw IoTThingManagerException("Fail to create Policy for $vehicleId", ex)
        } catch (ex: InvalidRequestException) {
            throw IoTThingManagerException("Fail to create Policy for $vehicleId", ex)
        }
        val cert = try {
            // create private / public keys and cert
            client.createKeysAndCertificate { builder ->
                builder.setAsActive(true)
            }
        } catch (ex: InvalidRequestException) {
            throw IoTThingManagerException("Fail to create Cert and Keys for $vehicleId", ex)
        }
        try {
            client.attachPolicy { builder ->
                builder.policyName(policy.policyName())
                    .target(cert.certificateArn())
            }
            client.attachThingPrincipal { builder ->
                builder.thingName(vehicleId)
                    .principal(cert.certificateArn())
            }
        } catch (ex: ResourceNotFoundException) {
            throw IoTThingManagerException("Fail to attach Policy or Principal for $vehicleId", ex)
        } catch (ex: InvalidRequestException) {
            throw IoTThingManagerException("Fail to attach Policy or Principal for $vehicleId", ex)
        }
        return cert
    }

    private fun deleteThing(vehicleId: String): Boolean {
        val policyName = Companion.POLICY_PREFIX + vehicleId + Companion.POLICY_POSTFIX
        try {
            // First we query a list of thing principals for this Thing.
            client.listThingPrincipals { builder ->
                builder.thingName(vehicleId).build()
            }.principals().forEach {
                // The sequence below is the fixed order of preparation work before deleting thing and policy
                client.detachThingPrincipal { builder ->
                    builder.thingName(vehicleId).principal(it).build()
                }
                client.detachPolicy { builder ->
                    builder.policyName(policyName).target(it).build()
                }
                client.updateCertificate { builder ->
                    builder.certificateId(it.substringAfterLast("/")).newStatus("INACTIVE").build()
                }
                client.deleteCertificate { builder ->
                    builder.certificateId(it.substringAfterLast("/")).forceDelete(true).build()
                }
            }
            client.deleteThing { builder ->
                builder.thingName(vehicleId)
            }
            client.deletePolicy { builder ->
                builder.policyName(policyName)
            }
        } catch (ex: ResourceNotFoundException) {
            // This exception can be common as Thing might no longer exist
            throw IoTThingManagerException("Fail to delete Thing for $vehicleId", ex)
        } catch (ex: InvalidRequestException) {
            throw IoTThingManagerException("Fail to delete Thing for $vehicleId", ex)
        }
        return true
    }

    /**
     * This function accept a map of vehicle id to vehicle simulation local folder.
     * It will invoke IoTClient to create thing for each vehicle and store the certificate, private key at
     * the provided path
     *
     * @param simConfigMap Mapping between vehicle id and its local simulation package folder
     * @return True if operation is successful
     * @throws IoTThingManagerException could be raised by following reasons:
     * 1) name is used by existing IoT Thing, Policy
     * 2) policy document is invalid
     * 3) The request to IoTClient API is invalid
     */
    fun createAndStoreThings(simConfigMap: Map<String, String>): Boolean {
        var status = true
        simConfigMap.forEach {
            val dir = File(it.value)
            if (dir.exists() && dir.isDirectory) {
                val cert = createThing(it.key)
                val certFile = File(it.value + "/cert.crt")
                certFile.createNewFile()
                certFile.writeText(cert.certificatePem().toString())
                val keyFile = File(it.value + "/pri.key")
                keyFile.createNewFile()
                keyFile.writeText(cert.keyPair().privateKey().toString())
                println("Thing ${it.key} is created. Cert and Private Key stored at ${it.value} ")
            } else {
                println("Thing ${it.key} is not created due to invalid directory")
                status = false
            }
        }
        return status
    }

    /**
     * This function accept a map of vehicle id to vehicle simulation local folder.
     * It will invoke IoTClient to delete thing for each vehicle and delete the local certificate, private key at
     * the provided path
     *
     * @param simConfigMap Mapping between vehicle id and its local simulation package folder
     * @return True if operation is successful
     * @throws IoTThingManagerException could be raised by following reasons:
     * 1) The IoT Thing, Policy name doesn't exist
     * 2) The request to IoTClient API is invalid
     */
    fun deleteThings(simConfigMap: Map<String, String>): Boolean {
        var status = true
        simConfigMap.forEach {
            deleteThing(it.key)
            println("Thing ${it.key} is deleted")
            val certFile = File(it.value + "/cert.crt")
            if (certFile.exists() && certFile.isFile) {
                certFile.delete()
            } else {
                status = false
                println("Unable to delete local copy of ${it.key} certificate")
            }
            val keyFile = File(it.value + "/pri.key")
            if (keyFile.exists() && keyFile.isFile) {
                keyFile.delete()
            } else {
                status = false
                println("Unable to delete local copy of ${it.key} private key")
            }
        }
        return status
    }

    /**
     * This function invoke IoTClient to return end point
     *
     * @return IoT End Point
     */
    fun getEndPoint(): String {
        return client.describeEndpoint { builder ->
            builder.endpointType("iot:Data-ATS")
        }.endpointAddress()
    }

    companion object {
        const val POLICY_PREFIX = "kfarm2-"
        const val POLICY_POSTFIX = "-policy"
        private const val POLICY_DOC = "{\n" +
            "  \"Version\": \"2012-10-17\",\n" +
            "  \"Statement\": [\n" +
            "    {\n" +
            "      \"Effect\": \"Allow\",\n" +
            "      \"Action\": [\n" +
            "        \"iot:*\"\n" +
            "      ],\n" +
            "      \"Resource\": [\n" +
            "        \"*\"\n" +
            "      ]\n" +
            "    }\n" +
            "  ]\n" +
            "}"
    }
}
