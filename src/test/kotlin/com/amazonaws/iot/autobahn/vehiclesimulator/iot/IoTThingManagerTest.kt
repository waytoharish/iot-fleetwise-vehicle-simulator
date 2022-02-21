package com.amazonaws.iot.autobahn.vehiclesimulator.iot

import com.amazonaws.iot.autobahn.vehiclesimulator.storage.S3Storage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import software.amazon.awssdk.services.iot.model.ListTargetsForPolicyRequest
import software.amazon.awssdk.services.iot.model.ListTargetsForPolicyResponse
import software.amazon.awssdk.services.iot.model.ListThingPrincipalsRequest
import software.amazon.awssdk.services.iot.model.ListThingPrincipalsResponse
import software.amazon.awssdk.services.iot.model.ResourceAlreadyExistsException
import software.amazon.awssdk.services.iot.model.ResourceNotFoundException
import software.amazon.awssdk.services.iot.model.UpdateCertificateRequest
import software.amazon.awssdk.services.iot.model.UpdateCertificateResponse
import java.util.function.Consumer

internal class IoTThingManagerTest {
    private val ioTClient = mockk<IotClient>()

    private val s3Storage = mockk<S3Storage>()

    private val iotThingManager = IoTThingManager(ioTClient, s3Storage)

    private val carList = listOf("car0", "car1", "car2", "car3")

    private val simulationMapping = carList.associateWith {
        "S3:://test_bucket/$it"
    }

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
    private val listTargetsForPolicyRequest = mutableListOf<Consumer<ListTargetsForPolicyRequest.Builder>>()
    private val describeEndPointRequest = mutableListOf<Consumer<DescribeEndpointRequest.Builder>>()

    @BeforeEach
    fun setup() {
        // Mock for S3 Storage
        every {
            s3Storage.put(any(), any())
        } returns Unit

        every {
            s3Storage.deleteObjectsFromSameBucket(any())
        } returns Unit

        // Mock IoT API calls
        every {
            ioTClient.createThing(capture(createThingRequestSlot))
        } returns CreateThingResponse.builder().build()

        every {
            ioTClient.createPolicy(capture(createPolicyRequestSlot))
        } returnsMany carList.map {
            CreatePolicyResponse.builder()
                .policyName("vehicle-simulator-policy").build()
        }

        every {
            ioTClient.createKeysAndCertificate(capture(createKeysAndCertificateRequestSlot))
        } returns
            CreateKeysAndCertificateResponse.builder()
                .certificateArn("certificate arn")
                .certificatePem("certificate")
                .keyPair { builder ->
                    builder
                        .privateKey("private key")
                        .publicKey("public key")
                        .build()
                }.build()

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
            ioTClient.listTargetsForPolicy(capture(listTargetsForPolicyRequest))
        } returns ListTargetsForPolicyResponse.builder().targets(listOf("cert")).build()

        every {
            ioTClient.describeEndpoint(capture(describeEndPointRequest))
        } returns DescribeEndpointResponse.builder().endpointAddress("endpointAddress").build()
    }

    @Test
    fun `When createAndStoreThings called with IoT Thing successfully created and stored`() {
        val result = runBlocking { iotThingManager.createAndStoreThings(simulationMapping) }
        Assertions.assertEquals(carList, result.first)
        Assertions.assertEquals(0, result.second.size)

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
        }.map { Assertions.assertEquals("vehicle-simulator-policy", it) }
        createKeysAndCertificateRequestSlot.map {
            val builder = CreateKeysAndCertificateRequest.builder()
            it.accept(builder)
            builder.build().setAsActive()
        }.forEach { Assertions.assertTrue(it) }
        attachPolicyRequestSlot.map {
            val builder = AttachPolicyRequest.builder()
            it.accept(builder)
            builder.build().policyName()
        }.map { Assertions.assertEquals("vehicle-simulator-policy", it) }
        attachThingPrincipalRequestSlot.map {
            val builder = AttachThingPrincipalRequest.builder()
            it.accept(builder)
            builder.build().thingName()
        }.forEachIndexed { index, element ->
            Assertions.assertEquals("car$index", element)
        }
        // Verify the S3 Storage API is invoked correctly
        carList.forEach {
            verify(exactly = 1) { s3Storage.put("S3:://test_bucket/$it/cert.crt", "certificate".toByteArray()) }
            verify(exactly = 1) { s3Storage.put("S3:://test_bucket/$it/pri.key", "private key".toByteArray()) }
        }
    }

    @Test
    fun `When createAndStoreThings called with IoT Thing already exist`() {
        // Here we mock car1 and car2 already exist. The createThing will throw resource exist exception
        every {
            ioTClient.createThing(capture(createThingRequestSlot))
        } throws ResourceAlreadyExistsException.builder().build() andThen
            CreateThingResponse.builder().build() andThenThrows
            ResourceAlreadyExistsException.builder().build() andThen
            CreateThingResponse.builder().build()
        val result = runBlocking { iotThingManager.createAndStoreThings(simulationMapping) }
        var recreatedCarList = deleteThingRequest.map {
            val builder = DeleteThingRequest.builder()
            it.accept(builder)
            builder.build().thingName()
        }
        Assertions.assertEquals(listOf("car0", "car1"), recreatedCarList)
        recreatedCarList = createThingRequestSlot.map {
            val builder = CreateThingRequest.builder()
            it.accept(builder)
            builder.build().thingName()
        }
        // There would be two call for car0 and car1. The first call raise exception, the second call after deletion.
        Assertions.assertEquals(listOf("car0", "car0", "car1", "car1", "car2", "car3"), recreatedCarList)
        Assertions.assertEquals(carList, result.first)
        Assertions.assertEquals(0, result.second.size)
    }

    @Test
    fun `When createAndStoreThings called with IoT Policy already exist`() {
        // Mock policy already exist hence the first API call raise resource already exist exception
        every {
            ioTClient.createPolicy(capture(createPolicyRequestSlot))
        } throws ResourceAlreadyExistsException.builder().build() andThen
            CreatePolicyResponse.builder().policyName("vehicle-simulator-policy").build()
        val result = runBlocking { iotThingManager.createAndStoreThings(simulationMapping) }
        createPolicyRequestSlot.map {
            val builder = CreatePolicyRequest.builder()
            it.accept(builder)
            builder.build().policyName()
        }.map { Assertions.assertEquals("vehicle-simulator-policy", it) }
        Assertions.assertEquals(carList, result.first)
        Assertions.assertEquals(0, result.second.size)
    }

    @Test
    fun `When deleteThings called with IoT Thing, cert and key deleted`() {
        val result = runBlocking { iotThingManager.deleteThings(simulationMapping) }
        Assertions.assertEquals(carList, result.first)
        Assertions.assertEquals(0, result.second.size)
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
        }.map { Assertions.assertEquals("vehicle-simulator-policy", it) }
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
        verify(exactly = 1) { s3Storage.deleteObjectsFromSameBucket(carList.map { "S3:://test_bucket/$it/cert.crt" }) }
        verify(exactly = 1) { s3Storage.deleteObjectsFromSameBucket(carList.map { "S3:://test_bucket/$it/pri.key" }) }
        deletePolicyRequest.map {
            val builder = DeletePolicyRequest.builder()
            it.accept(builder)
            builder.build().policyName()
        }.map { Assertions.assertEquals("vehicle-simulator-policy", it) }
    }

    @Test
    fun `When deleteThings called with IoT Thing Not Found`() {
        // Mock ResourceNotFoundException as the code will log exception error message
        mockkConstructor(ResourceNotFoundException::class)
        every { anyConstructed<ResourceNotFoundException>().awsErrorDetails().errorMessage() } returns "IoT Thing Not Found"
        every {
            ioTClient.listThingPrincipals(capture(listThingPrincipalRequestSlot))
        } throws ResourceNotFoundException.builder().build()
        val result = runBlocking { iotThingManager.deleteThings(simulationMapping) }
        Assertions.assertEquals(carList, result.first)
        Assertions.assertEquals(0, result.second.size)
    }

    @Test
    fun `When deleteThings called with IoT Policy Not Found`() {
        every {
            ioTClient.deletePolicy(capture(deletePolicyRequest))
        } throws ResourceNotFoundException.builder().build()
        val result = runBlocking { iotThingManager.deleteThings(simulationMapping) }
        Assertions.assertEquals(carList, result.first)
        Assertions.assertEquals(0, result.second.size)
    }

    @Test
    fun getIoTCoreDataEndPoint() {
        val endpointAddress = iotThingManager.getIoTCoreDataEndPoint()
        describeEndPointRequest.map {
            val builder = DescribeEndpointRequest.builder()
            it.accept(builder)
            builder.build().endpointType()
        }.forEach {
            Assertions.assertEquals("iot:Data-ATS", it)
        }
        Assertions.assertEquals("endpointAddress", endpointAddress)
    }
}
