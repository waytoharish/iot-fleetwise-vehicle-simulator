package com.amazonaws.iot.autobahn.vehiclesimulator.iot

import com.amazonaws.iot.autobahn.vehiclesimulator.iot.IoTThingManager.Companion.CERTIFICATE_FILE_NAME
import com.amazonaws.iot.autobahn.vehiclesimulator.iot.IoTThingManager.Companion.DEFAULT_POLICY_NAME
import com.amazonaws.iot.autobahn.vehiclesimulator.iot.IoTThingManager.Companion.PRIVATE_KEY_FILE_NAME
import com.amazonaws.iot.autobahn.vehiclesimulator.storage.S3Storage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.services.iot.IotAsyncClient
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
import software.amazon.awssdk.services.iot.model.DeleteConflictException
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
import software.amazon.awssdk.services.iot.model.ListTargetsForPolicyRequest
import software.amazon.awssdk.services.iot.model.ListTargetsForPolicyResponse
import software.amazon.awssdk.services.iot.model.ListThingPrincipalsRequest
import software.amazon.awssdk.services.iot.model.ListThingPrincipalsResponse
import software.amazon.awssdk.services.iot.model.ResourceAlreadyExistsException
import software.amazon.awssdk.services.iot.model.ResourceNotFoundException
import software.amazon.awssdk.services.iot.model.UpdateCertificateRequest
import software.amazon.awssdk.services.iot.model.UpdateCertificateResponse
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

internal class IoTThingManagerTest {
    private val ioTClient = mockk<IotAsyncClient>()

    private val s3Storage = mockk<S3Storage>()

    private val iotThingManager = IoTThingManager(ioTClient, s3Storage)

    private val carList = listOf("car0", "car1", "car2", "car3")

    private val simulationMapping = listOf("car0", "car1").associateWith {
        S3Storage.Companion.BucketAndKey("test_bucket_a", it)
    } + listOf("car2", "car3").associateWith {
        S3Storage.Companion.BucketAndKey("test_bucket_b", it)
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
        println("start setting up")
        // Mock for S3 Storage
        coEvery {
            s3Storage.put(any(), any(), any())
        } returns Unit

        coEvery {
            s3Storage.deleteObjects(any(), any())
        } returns Unit

        // Mock IoT API calls
        every {
            ioTClient.createThing(capture(createThingRequestSlot))
        } returns CompletableFuture.completedFuture(CreateThingResponse.builder().build())

        every {
            ioTClient.createPolicy(capture(createPolicyRequestSlot))
        } returns CompletableFuture.completedFuture(
            CreatePolicyResponse.builder()
                .policyName(DEFAULT_POLICY_NAME).build()
        )

        every {
            ioTClient.createKeysAndCertificate(capture(createKeysAndCertificateRequestSlot))
        } returns CompletableFuture.completedFuture(
            CreateKeysAndCertificateResponse.builder()
                .certificateArn("certificate arn")
                .certificatePem("certificate")
                .keyPair { builder ->
                    builder
                        .privateKey("private key")
                        .publicKey("public key")
                        .build()
                }.build()
        )

        every {
            ioTClient.attachPolicy(capture(attachPolicyRequestSlot))
        } returns CompletableFuture.completedFuture(AttachPolicyResponse.builder().build())

        every {
            ioTClient.attachThingPrincipal(capture(attachThingPrincipalRequestSlot))
        } returns CompletableFuture.completedFuture(AttachThingPrincipalResponse.builder().build())

        every {
            ioTClient.listThingPrincipals(capture(listThingPrincipalRequestSlot))
        } returnsMany carList.map {
            CompletableFuture.completedFuture(ListThingPrincipalsResponse.builder().principals("principal/$it").build())
        }

        every {
            ioTClient.detachThingPrincipal(capture(detachThingPrincipalRequest))
        } returns CompletableFuture.completedFuture(DetachThingPrincipalResponse.builder().build())

        every {
            ioTClient.detachPolicy(capture(detachPolicyRequest))
        } returns CompletableFuture.completedFuture(DetachPolicyResponse.builder().build())

        every {
            ioTClient.updateCertificate(capture(updateCertificateRequest))
        } returns CompletableFuture.completedFuture(UpdateCertificateResponse.builder().build())

        every {
            ioTClient.deleteCertificate(capture(deleteCertificateRequest))
        } returns CompletableFuture.completedFuture(DeleteCertificateResponse.builder().build())

        every {
            ioTClient.deleteThing(capture(deleteThingRequest))
        } returns CompletableFuture.completedFuture(DeleteThingResponse.builder().build())

        every {
            ioTClient.deletePolicy(capture(deletePolicyRequest))
        } returns CompletableFuture.completedFuture(DeletePolicyResponse.builder().build())

        every {
            ioTClient.listTargetsForPolicy(capture(listTargetsForPolicyRequest))
        } returns CompletableFuture.completedFuture(ListTargetsForPolicyResponse.builder().targets(listOf("cert")).build())

        every {
            ioTClient.describeEndpoint(capture(describeEndPointRequest))
        } returns CompletableFuture.completedFuture(DescribeEndpointResponse.builder().endpointAddress("endpointAddress").build())
    }

    @Test
    fun `When createAndStoreThings called with IoT Thing successfully created and stored`() {
        val result = runBlocking(Dispatchers.IO) {
            iotThingManager.createAndStoreThings(simulationMapping)
        }
        Assertions.assertEquals(carList, result.successList)
        Assertions.assertEquals(0, result.failedList.size)

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
        }.map { Assertions.assertEquals(DEFAULT_POLICY_NAME, it) }
        createKeysAndCertificateRequestSlot.map {
            val builder = CreateKeysAndCertificateRequest.builder()
            it.accept(builder)
            builder.build().setAsActive()
        }.forEach { Assertions.assertTrue(it) }
        attachPolicyRequestSlot.map {
            val builder = AttachPolicyRequest.builder()
            it.accept(builder)
            builder.build().policyName()
        }.map { Assertions.assertEquals(DEFAULT_POLICY_NAME, it) }
        attachThingPrincipalRequestSlot.map {
            val builder = AttachThingPrincipalRequest.builder()
            it.accept(builder)
            builder.build().thingName()
        }.forEachIndexed { index, element ->
            Assertions.assertEquals("car$index", element)
        }
        // Verify the S3 Storage API is invoked correctly
        listOf("car0", "car1").forEach {
            coVerify(exactly = 1) {
                s3Storage.put("test_bucket_a", "$it/$CERTIFICATE_FILE_NAME", "certificate".toByteArray())
            }
            coVerify(exactly = 1) {
                s3Storage.put("test_bucket_a", "$it/$PRIVATE_KEY_FILE_NAME", "private key".toByteArray())
            }
        }
        listOf("car2", "car3").forEach {
            coVerify(exactly = 1) {
                s3Storage.put("test_bucket_b", "$it/$CERTIFICATE_FILE_NAME", "certificate".toByteArray())
            }
            coVerify(exactly = 1) {
                s3Storage.put("test_bucket_b", "$it/$PRIVATE_KEY_FILE_NAME", "private key".toByteArray())
            }
        }
    }

    @Test
    fun `When createAndStoreThings called with IoT Thing already exist`() {
        // Here we mock car1 and car2 already exist. The createThing will throw resource exist exception
        every {
            ioTClient.createThing(capture(createThingRequestSlot))
        } throws ResourceAlreadyExistsException.builder().build() andThen
            CompletableFuture.completedFuture(CreateThingResponse.builder().build()) andThenThrows
            ResourceAlreadyExistsException.builder().build() andThen
            CompletableFuture.completedFuture(CreateThingResponse.builder().build())
        val result = runBlocking(Dispatchers.IO) {
            iotThingManager.createAndStoreThings(simulationMapping)
        }
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
        Assertions.assertEquals(carList, result.successList)
        Assertions.assertEquals(0, result.failedList.size)
    }

    @Test
    fun `When createAndStoreThings called with IoT Policy already exist and recreate`() {
        // Mock policy already exist hence the first API call raise resource already exist exception
        every {
            ioTClient.createPolicy(capture(createPolicyRequestSlot))
        } throws ResourceAlreadyExistsException.builder().build() andThen
            CompletableFuture.completedFuture(CreatePolicyResponse.builder().policyName(DEFAULT_POLICY_NAME).build())
        // We set the flag to recreate policy if exists
        val result = runBlocking(Dispatchers.IO) {
            iotThingManager.createAndStoreThings(simulationMapping, recreatePolicyIfAlreadyExists = true)
        }
        createPolicyRequestSlot.map {
            val builder = CreatePolicyRequest.builder()
            it.accept(builder)
            builder.build().policyName()
        }.map { Assertions.assertEquals(DEFAULT_POLICY_NAME, it) }
        Assertions.assertEquals(carList, result.successList)
        Assertions.assertEquals(0, result.failedList.size)
    }

    @Test
    fun `When createAndStoreThings called with IoT Policy already exist and reuse`() {
        // Mock policy already exist hence the first API call raise resource already exist exception
        every {
            ioTClient.createPolicy(capture(createPolicyRequestSlot))
        } throws ResourceAlreadyExistsException.builder().build() andThen
            CompletableFuture.completedFuture(CreatePolicyResponse.builder().policyName(DEFAULT_POLICY_NAME).build())
        // We set the flag to recreate policy if exists
        val result = runBlocking(Dispatchers.IO) {
            iotThingManager.createAndStoreThings(simulationMapping, recreatePolicyIfAlreadyExists = false)
        }
        createPolicyRequestSlot.map {
            val builder = CreatePolicyRequest.builder()
            it.accept(builder)
            builder.build().policyName()
        }.map { Assertions.assertEquals(DEFAULT_POLICY_NAME, it) }
        Assertions.assertEquals(carList, result.successList)
        Assertions.assertEquals(0, result.failedList.size)
    }

    @Test
    fun `When deleteThings called with IoT Thing, cert and key deleted`() {
        val result = runBlocking { iotThingManager.deleteThings(simulationMapping, deletePolicy = true) }
        Assertions.assertEquals(carList, result.successList)
        Assertions.assertEquals(0, result.failedList.size)
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
        listTargetsForPolicyRequest.map {
            val builder = ListTargetsForPolicyRequest.builder()
            it.accept(builder)
            builder.build().policyName()
        }.map { Assertions.assertEquals(DEFAULT_POLICY_NAME, it) }
        detachPolicyRequest.map {
            val builder = DetachPolicyRequest.builder()
            it.accept(builder)
            builder.build().policyName()
        }.map { Assertions.assertEquals(DEFAULT_POLICY_NAME, it) }
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
        coVerify(exactly = 1) {
            s3Storage.deleteObjects(
                "test_bucket_a",
                listOf("car0", "car1").flatMap {
                    listOf("$it/$CERTIFICATE_FILE_NAME", "$it/$PRIVATE_KEY_FILE_NAME")
                }
            )
        }
        coVerify(exactly = 1) {
            s3Storage.deleteObjects(
                "test_bucket_b",
                listOf("car2", "car3").flatMap {
                    listOf("$it/$CERTIFICATE_FILE_NAME", "$it/$PRIVATE_KEY_FILE_NAME")
                }
            )
        }
        deletePolicyRequest.map {
            val builder = DeletePolicyRequest.builder()
            it.accept(builder)
            builder.build().policyName()
        }.map { Assertions.assertEquals(DEFAULT_POLICY_NAME, it) }
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
        Assertions.assertEquals(carList, result.successList)
        Assertions.assertEquals(0, result.failedList.size)
    }

    @Test
    fun `When deleteThings called with deleteThing first failed then succeed`() {
        // Mock InvalidRequestException first. The code will retry and succeed
        every {
            ioTClient.deleteThing(capture(deleteThingRequest))
        } throws InvalidRequestException.builder().build() andThen
            CompletableFuture.completedFuture(DeleteThingResponse.builder().build())
        val result = runBlocking { iotThingManager.deleteThings(simulationMapping) }
        Assertions.assertEquals(carList, result.successList)
        Assertions.assertEquals(0, result.failedList.size)
    }

    @Test
    fun `When deleteThings called with IoT Policy Not Found`() {
        every {
            ioTClient.deletePolicy(capture(deletePolicyRequest))
        } throws ResourceNotFoundException.builder().build()
        val result = runBlocking { iotThingManager.deleteThings(simulationMapping, deletePolicy = true) }
        Assertions.assertEquals(carList, result.successList)
        Assertions.assertEquals(0, result.failedList.size)
    }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun `When deleteThings called with iotClient deleteThing failed all the retries`() {
        every {
            ioTClient.deleteThing(capture(deleteThingRequest))
        } throws InvalidRequestException.builder().build()
        assertThrows<InvalidRequestException> {
            runBlockingTest {
                iotThingManager.deleteThings(simulationMapping, deletePolicy = true)
            }
        }
    }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun `When deleteThings called with iotClient deleteCertificate failed all the retries`() {
        every {
            ioTClient.deleteCertificate(capture(deleteCertificateRequest))
        } throws DeleteConflictException.builder().build()
        runBlockingTest {
            iotThingManager.deleteThings(simulationMapping, deletePolicy = true)
        }
    }

    @Test
    fun getIoTCoreDataEndPoint() {
        val endpointAddress = runBlocking(Dispatchers.IO) { iotThingManager.getIoTCoreDataEndPoint() }
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
