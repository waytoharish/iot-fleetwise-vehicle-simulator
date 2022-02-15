package com.amazonaws.iot.autobahn.vehiclesimulator.iot

import com.amazonaws.iot.autobahn.vehiclesimulator.exceptions.IoTThingManagerException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import software.amazon.awssdk.services.iot.IotClient
import software.amazon.awssdk.services.iot.model.AttachPolicyRequest
import software.amazon.awssdk.services.iot.model.AttachPolicyResponse
import software.amazon.awssdk.services.iot.model.AttachThingPrincipalRequest
import software.amazon.awssdk.services.iot.model.AttachThingPrincipalResponse
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateRequest
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateResponse
import software.amazon.awssdk.services.iot.model.CreatePolicyRequest
import software.amazon.awssdk.services.iot.model.CreatePolicyResponse
import software.amazon.awssdk.services.iot.model.CreateThingRequest
import software.amazon.awssdk.services.iot.model.CreateThingResponse
import software.amazon.awssdk.services.iot.model.DeleteCertificateRequest
import software.amazon.awssdk.services.iot.model.DeleteCertificateResponse
import software.amazon.awssdk.services.iot.model.DeletePolicyRequest
import software.amazon.awssdk.services.iot.model.DeletePolicyResponse
import software.amazon.awssdk.services.iot.model.DeleteThingRequest
import software.amazon.awssdk.services.iot.model.DeleteThingResponse
import software.amazon.awssdk.services.iot.model.DescribeEndpointRequest
import software.amazon.awssdk.services.iot.model.DescribeEndpointResponse
import software.amazon.awssdk.services.iot.model.DetachPolicyRequest
import software.amazon.awssdk.services.iot.model.DetachPolicyResponse
import software.amazon.awssdk.services.iot.model.DetachThingPrincipalRequest
import software.amazon.awssdk.services.iot.model.DetachThingPrincipalResponse
import software.amazon.awssdk.services.iot.model.InvalidRequestException
import software.amazon.awssdk.services.iot.model.ListThingPrincipalsRequest
import software.amazon.awssdk.services.iot.model.ListThingPrincipalsResponse
import software.amazon.awssdk.services.iot.model.MalformedPolicyException
import software.amazon.awssdk.services.iot.model.ResourceAlreadyExistsException
import software.amazon.awssdk.services.iot.model.ResourceNotFoundException
import software.amazon.awssdk.services.iot.model.UpdateCertificateRequest
import software.amazon.awssdk.services.iot.model.UpdateCertificateResponse
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.function.Consumer

internal class IoTThingManagerTest {

    // We cannot mock File as it's not supported in mockk. We write cert/key to temporary folders of JUnit
    @TempDir
    @JvmField
    var tempFolder: File? = null

    private val ioTClient = mockk<IotClient>()

    private val iotThingManager = IoTThingManager(ioTClient)

    private val carList = listOf("car0", "car1", "car2", "car3")

    private val createThingRequestSlot = mutableListOf<Consumer<CreateThingRequest.Builder>>()
    private val createPolicyRequestSlot = mutableListOf<Consumer<CreatePolicyRequest.Builder>>()
    private val createKeysAndCertificateRequestSlot = mutableListOf<Consumer<CreateKeysAndCertificateRequest.Builder>>()
    private val attachPolicyRequestSlot = mutableListOf<Consumer<AttachPolicyRequest.Builder>>()
    private val attachThingPrincipalRequestSlot = mutableListOf<Consumer<AttachThingPrincipalRequest.Builder>>()
    private val listThingPrincipalRequestSlot = mutableListOf<Consumer<ListThingPrincipalsRequest.Builder>>()
    private val detachThingPrincipalRequest = mutableListOf<Consumer<DetachThingPrincipalRequest.Builder>>()
    private val detachPolicyRequest = mutableListOf<Consumer<DetachPolicyRequest.Builder>>()
    private val updateCertificateRequest = mutableListOf<Consumer<UpdateCertificateRequest.Builder>>()
    private val deleteCertificateRequest = mutableListOf<Consumer<DeleteCertificateRequest.Builder>>()
    private val deleteThingRequest = mutableListOf<Consumer<DeleteThingRequest.Builder>>()
    private val deletePolicyRequest = mutableListOf<Consumer<DeletePolicyRequest.Builder>>()
    private val describeEndPointRequest = mutableListOf<Consumer<DescribeEndpointRequest.Builder>>()

    @BeforeEach
    fun setup() {
        // Mock IoT API calls
        every {
            ioTClient.createThing(capture(createThingRequestSlot))
        } returns CreateThingResponse.builder().build()

        every {
            ioTClient.createPolicy(capture(createPolicyRequestSlot))
        } returnsMany carList.map {
            CreatePolicyResponse.builder()
                .policyName("${IoTThingManager.POLICY_PREFIX}$it${IoTThingManager.POLICY_POSTFIX}").build()
        }

        every {
            ioTClient.createKeysAndCertificate(capture(createKeysAndCertificateRequestSlot))
        } returnsMany carList.map {
            CreateKeysAndCertificateResponse.builder()
                .certificatePem("Certificate for $it")
                .keyPair { builder ->
                    builder
                        .privateKey("private key for $it")
                        .publicKey("public key for $it")
                        .build()
                }.build()
        }

        every {
            ioTClient.attachPolicy(capture(attachPolicyRequestSlot))
        } returns AttachPolicyResponse.builder().build()

        every {
            ioTClient.attachThingPrincipal(capture(attachThingPrincipalRequestSlot))
        } returns AttachThingPrincipalResponse.builder().build()

        every {
            ioTClient.listThingPrincipals(capture(listThingPrincipalRequestSlot))
        } returnsMany carList.map {
            ListThingPrincipalsResponse.builder().principals("principal/$it").build()
        }

        every {
            ioTClient.detachThingPrincipal(capture(detachThingPrincipalRequest))
        } returns DetachThingPrincipalResponse.builder().build()

        every {
            ioTClient.detachPolicy(capture(detachPolicyRequest))
        } returns DetachPolicyResponse.builder().build()

        every {
            ioTClient.updateCertificate(capture(updateCertificateRequest))
        } returns UpdateCertificateResponse.builder().build()

        every {
            ioTClient.deleteCertificate(capture(deleteCertificateRequest))
        } returns DeleteCertificateResponse.builder().build()

        every {
            ioTClient.deleteThing(capture(deleteThingRequest))
        } returns DeleteThingResponse.builder().build()

        every {
            ioTClient.deletePolicy(capture(deletePolicyRequest))
        } returns DeletePolicyResponse.builder().build()

        every {
            ioTClient.describeEndpoint(capture(describeEndPointRequest))
        } returns DescribeEndpointResponse.builder().endpointAddress("endpointAddress").build()
    }

    @Test
    fun `When createAndStoreThings called with IoT Thing successfully created and stored`() {
        val simulationMapping = carList.associateWith {
            tempFolder?.toPath().toString() + "/$it"
        }
        // create directory for each car
        simulationMapping.forEach { Files.createDirectory(Paths.get(it.value)) }

        val status = iotThingManager.createAndStoreThings(simulationMapping)
        Assertions.assertEquals(true, status)
        // Verify the cert and key are correctly written
        simulationMapping.forEach {
            Assertions.assertEquals(
                "Certificate for ${it.key}",
                File(it.value + "/cert.crt").readText()
            )
            Assertions.assertEquals(
                "private key for ${it.key}",
                File(it.value + "/pri.key").readText()
            )
        }
        createThingRequestSlot.map {
            val builder = CreateThingRequest.builder()
            it.accept(builder)
            builder.build().thingName()
        }.forEachIndexed { index, element ->
            Assertions.assertEquals("car$index", element)
        }
        createPolicyRequestSlot.map {
            val builder = CreatePolicyRequest.builder()
            it.accept(builder)
            builder.build().policyName()
        }.forEachIndexed { index, element ->
            Assertions.assertEquals("${IoTThingManager.POLICY_PREFIX}car$index${IoTThingManager.POLICY_POSTFIX}", element)
        }
        createKeysAndCertificateRequestSlot.map {
            val builder = CreateKeysAndCertificateRequest.builder()
            it.accept(builder)
            builder.build().setAsActive()
        }.forEach { Assertions.assertTrue(it) }
        attachPolicyRequestSlot.map {
            val builder = AttachPolicyRequest.builder()
            it.accept(builder)
            builder.build().policyName()
        }.forEachIndexed { index, element ->
            Assertions.assertEquals("${IoTThingManager.POLICY_PREFIX}${carList[index]}${IoTThingManager.POLICY_POSTFIX}", element)
        }
        attachThingPrincipalRequestSlot.map {
            val builder = AttachThingPrincipalRequest.builder()
            it.accept(builder)
            builder.build().thingName()
        }.forEachIndexed { index, element ->
            Assertions.assertEquals("car$index", element)
        }
    }

    @Test
    fun `When createAndStoreThings called with IoTClient createThing raise exception`() {
        val simulationMapping = carList.associateWith {
            tempFolder?.toPath().toString() + "/$it"
        }
        // create directory for each car
        simulationMapping.forEach { Files.createDirectory(Paths.get(it.value)) }
        listOf(
            ResourceAlreadyExistsException.builder().build(),
            InvalidRequestException.builder().build()
        ).forEach {
            every {
                ioTClient.createThing(capture(createThingRequestSlot))
            } throws it
            assertThrows<IoTThingManagerException> { iotThingManager.createAndStoreThings(simulationMapping) }
        }
    }

    @Test
    fun `When createAndStoreThings called with IoTClient createPolicy raise exception`() {
        val simulationMapping = carList.associateWith {
            tempFolder?.toPath().toString() + "/$it"
        }
        // create directory for each car
        simulationMapping.forEach { Files.createDirectory(Paths.get(it.value)) }
        listOf(
            ResourceAlreadyExistsException.builder().build(),
            MalformedPolicyException.builder().build(),
            InvalidRequestException.builder().build()
        ).forEach {
            every {
                ioTClient.createPolicy(any<Consumer<CreatePolicyRequest.Builder>>())
            } throws it
            assertThrows<IoTThingManagerException> { iotThingManager.createAndStoreThings(simulationMapping) }
        }
    }

    @Test
    fun `When createAndStoreThings called with IoTClient createKeysAndCertificate raise exception`() {
        val simulationMapping = carList.associateWith {
            tempFolder?.toPath().toString() + "/$it"
        }
        // create directory for each car
        simulationMapping.forEach { Files.createDirectory(Paths.get(it.value)) }
        listOf(
            InvalidRequestException.builder().build()
        ).forEach {
            every {
                ioTClient.createKeysAndCertificate(capture(createKeysAndCertificateRequestSlot))
            } throws it
            assertThrows<IoTThingManagerException> { iotThingManager.createAndStoreThings(simulationMapping) }
        }
    }

    @Test
    fun `When createAndStoreThings called with IoTClient attachPolicy raise exception`() {
        val simulationMapping = carList.associateWith {
            tempFolder?.toPath().toString() + "/$it"
        }
        // create directory for each car
        simulationMapping.forEach { Files.createDirectory(Paths.get(it.value)) }
        listOf(
            ResourceNotFoundException.builder().build(),
            InvalidRequestException.builder().build()
        ).forEach {
            every {
                ioTClient.attachPolicy(capture(attachPolicyRequestSlot))
            } throws it
            assertThrows<IoTThingManagerException> { iotThingManager.createAndStoreThings(simulationMapping) }
        }
    }

    @Test
    fun `When createAndStoreThings called with IoTClient attachThingPrincipal raise exception`() {
        val simulationMapping = carList.associateWith {
            tempFolder?.toPath().toString() + "/$it"
        }
        // create directory for each car
        simulationMapping.forEach { Files.createDirectory(Paths.get(it.value)) }
        listOf(
            ResourceNotFoundException.builder().build(),
            InvalidRequestException.builder().build()
        ).forEach {
            every {
                ioTClient.attachThingPrincipal(capture(attachThingPrincipalRequestSlot))
            } throws it
            assertThrows<IoTThingManagerException> { iotThingManager.createAndStoreThings(simulationMapping) }
        }
    }

    @Test
    fun `When createAndStoreThings called with invalid directory`() {
        val simulationMapping = carList.associateWith {
            tempFolder?.toPath().toString() + "/$it"
        }
        var status = iotThingManager.createAndStoreThings(simulationMapping)
        // We intentionally pass in a non exist folder. We shall expect it return false as store failed
        Assertions.assertEquals(false, status)
        simulationMapping.forEach { Files.createFile(Paths.get(it.value)) }
        status = iotThingManager.createAndStoreThings(simulationMapping)
        // We intentionally pass in files instead of folders. We shall expect it return false as store failed
        Assertions.assertEquals(false, status)
    }

    @Test
    fun `When deleteThings called with IoT Thing, cert and key deleted`() {
        val simulationMapping = carList.associateWith {
            tempFolder?.toPath().toString() + "/$it"
        }
        simulationMapping.forEach {
            Files.createDirectory(Paths.get(it.value))
            Files.createFile(Paths.get(it.value + "/cert.crt"))
            Files.createFile(Paths.get(it.value + "/pri.key"))
        }
        val status = iotThingManager.deleteThings(simulationMapping)
        Assertions.assertEquals(true, status)
        listThingPrincipalRequestSlot.map {
            val builder = ListThingPrincipalsRequest.builder()
            it.accept(builder)
            builder.build().thingName()
        }.forEachIndexed { index, element ->
            Assertions.assertEquals(carList[index], element)
        }
        detachThingPrincipalRequest.map {
            val builder = DetachThingPrincipalRequest.builder()
            it.accept(builder)
            builder.build().thingName()
        }.forEachIndexed { index, element ->
            Assertions.assertEquals(carList[index], element)
        }
        detachPolicyRequest.map {
            val builder = DetachPolicyRequest.builder()
            it.accept(builder)
            builder.build().policyName()
        }.forEachIndexed { index, element ->
            Assertions.assertEquals("${IoTThingManager.POLICY_PREFIX}${carList[index]}${IoTThingManager.POLICY_POSTFIX}", element)
        }
        updateCertificateRequest.map {
            val builder = UpdateCertificateRequest.builder()
            it.accept(builder)
            builder.build().newStatusAsString()
        }.forEach { Assertions.assertEquals("INACTIVE", it) }
        deleteCertificateRequest.map {
            val builder = DeleteCertificateRequest.builder()
            it.accept(builder)
            builder.build().certificateId()
        }.forEachIndexed { index, element ->
            Assertions.assertEquals(carList[index], element)
        }
        deleteThingRequest.map {
            val builder = DeleteThingRequest.builder()
            it.accept(builder)
            builder.build().thingName()
        }.forEachIndexed { index, element ->
            Assertions.assertEquals(carList[index], element)
        }
        deletePolicyRequest.map {
            val builder = DeletePolicyRequest.builder()
            it.accept(builder)
            builder.build().policyName()
        }.forEachIndexed { index, element ->
            Assertions.assertEquals("${IoTThingManager.POLICY_PREFIX}${carList[index]}${IoTThingManager.POLICY_POSTFIX}", element)
        }
    }

    @Test
    fun `When deleteThings called with ioTClient raise exception`() {
        val simulationMapping = carList.associateWith {
            tempFolder?.toPath().toString() + "/$it"
        }
        every {
            ioTClient.listThingPrincipals(capture(listThingPrincipalRequestSlot))
        } throws ResourceNotFoundException.builder().build()
        assertThrows<IoTThingManagerException> { iotThingManager.deleteThings(simulationMapping) }
        every {
            ioTClient.listThingPrincipals(capture(listThingPrincipalRequestSlot))
        } throws InvalidRequestException.builder().build()
        assertThrows<IoTThingManagerException> { iotThingManager.deleteThings(simulationMapping) }
    }

    @Test
    fun `When deleteThings called with IoT Thing deleted but cert and key not deleted`() {
        val simulationMapping = carList.associateWith {
            tempFolder?.toPath().toString() + "/$it"
        }
        var status = iotThingManager.deleteThings(simulationMapping)
        Assertions.assertEquals(false, status)
        simulationMapping.forEach {
            Files.createDirectory(Paths.get(it.value))
            Files.createFile(Paths.get(it.value + "/cert.crt"))
        }
        status = iotThingManager.deleteThings(simulationMapping)
        Assertions.assertEquals(false, status)
        simulationMapping.forEach {
            Files.deleteIfExists(Paths.get(it.value + "/cert.crt"))
            Files.createFile(Paths.get(it.value + "/pri.key"))
        }
        status = iotThingManager.deleteThings(simulationMapping)
        Assertions.assertEquals(false, status)
        simulationMapping.forEach {
            Files.deleteIfExists(Paths.get(it.value + "/pri.key"))
            Files.createDirectory(Paths.get(it.value + "/cert.crt"))
            Files.createDirectory(Paths.get(it.value + "/pri.key"))
        }
        status = iotThingManager.deleteThings(simulationMapping)
        Assertions.assertEquals(false, status)
    }

    @Test
    fun getEndPoint() {
        describeEndPointRequest.map {
            val builder = DescribeEndpointRequest.builder()
            it.accept(builder)
            builder.build().endpointType()
        }.forEach {
            Assertions.assertEquals("iot:Data-ATS", it)
        }
        Assertions.assertEquals("endpointAddress", iotThingManager.getEndPoint())
    }
}
