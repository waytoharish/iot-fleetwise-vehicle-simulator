package com.amazonaws.iot.autobahn.vehiclesimulator.iot

import com.amazonaws.iot.autobahn.vehiclesimulator.S3
import com.amazonaws.iot.autobahn.vehiclesimulator.SimulationMetaData
import com.amazonaws.iot.autobahn.vehiclesimulator.exceptions.CertificateDeletionException
import com.amazonaws.iot.autobahn.vehiclesimulator.iot.IoTThingManager.Companion.CERTIFICATE_FILE_NAME
import com.amazonaws.iot.autobahn.vehiclesimulator.iot.IoTThingManager.Companion.DEFAULT_POLICY_NAME
import com.amazonaws.iot.autobahn.vehiclesimulator.iot.IoTThingManager.Companion.PRIVATE_KEY_FILE_NAME
import com.amazonaws.iot.autobahn.vehiclesimulator.iot.IoTThingManager.Companion.getIamPolicy
import com.amazonaws.iot.autobahn.vehiclesimulator.storage.S3Storage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.CreateRoleRequest
import software.amazon.awssdk.services.iam.model.CreateRoleResponse
import software.amazon.awssdk.services.iam.model.EntityAlreadyExistsException
import software.amazon.awssdk.services.iam.model.GetRoleRequest
import software.amazon.awssdk.services.iam.model.GetRoleResponse
import software.amazon.awssdk.services.iam.model.PutRolePolicyRequest
import software.amazon.awssdk.services.iam.model.PutRolePolicyResponse
import software.amazon.awssdk.services.iam.model.Role
import software.amazon.awssdk.services.iot.IotAsyncClient
import software.amazon.awssdk.services.iot.model.AttachPolicyRequest
import software.amazon.awssdk.services.iot.model.AttachPolicyResponse
import software.amazon.awssdk.services.iot.model.AttachThingPrincipalRequest
import software.amazon.awssdk.services.iot.model.AttachThingPrincipalResponse
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateRequest
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateResponse
import software.amazon.awssdk.services.iot.model.CreatePolicyRequest
import software.amazon.awssdk.services.iot.model.CreatePolicyResponse
import software.amazon.awssdk.services.iot.model.CreateRoleAliasRequest
import software.amazon.awssdk.services.iot.model.CreateRoleAliasResponse
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
import software.amazon.awssdk.services.iot.model.DescribeRoleAliasRequest
import software.amazon.awssdk.services.iot.model.DescribeRoleAliasResponse
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
import software.amazon.awssdk.services.iot.model.RoleAliasDescription
import software.amazon.awssdk.services.iot.model.UpdateCertificateRequest
import software.amazon.awssdk.services.iot.model.UpdateCertificateResponse
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

internal class IoTThingManagerTest {
    private val ioTClient = mockk<IotAsyncClient>()

    private val s3Storage = mockk<S3Storage>()

    private val iamClient = mockk<IamClient>()

    private val iotThingManager = IoTThingManager(ioTClient, s3Storage, iamClient)

    private val carList = setOf("car0", "car1", "car2", "car3")

    private val simulationMapping = listOf("car0", "car1").map {
        SimulationMetaData(it, S3("test_bucket_a", it))
    } +
        listOf("car2", "car3").map {
            SimulationMetaData(it, S3("test_bucket_b", it))
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
        Assertions.assertTrue(carList == result.successList)
        Assertions.assertEquals(0, result.failedList.size)

        Assertions.assertTrue(
            carList ==
                createThingRequestSlot.map {
                    val builder = CreateThingRequest.builder()
                    it.accept(builder)
                    builder.build().thingName()
                }.toSet()
        )
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
        Assertions.assertTrue(
            carList ==
                attachThingPrincipalRequestSlot.map {
                    val builder = AttachThingPrincipalRequest.builder()
                    it.accept(builder)
                    builder.build().thingName()
                }.toSet()
        )
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
        // We shall expect two re-creation Thing as we mock createThing to throw ResourceAlreadyExists twice
        Assertions.assertEquals(
            2,
            deleteThingRequest.map {
                val builder = DeleteThingRequest.builder()
                it.accept(builder)
                builder.build().thingName()
            }.size
        )
        // There would be two additional call due to recreation. The first call raise exception, the second call after deletion.
        Assertions.assertEquals(
            carList.size + 2,
            createThingRequestSlot.map {
                val builder = CreateThingRequest.builder()
                it.accept(builder)
                builder.build().thingName()
            }.size
        )
        Assertions.assertEquals(carList, result.successList)
        Assertions.assertEquals(0, result.failedList.size)
    }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun `When createAndStoreThings called with IoT Thing already exist and IoT Thing deletion failed`() {
        // Here we mock car1 and car2 already exist. The createThing will throw resource exist exception
        every {
            ioTClient.createThing(capture(createThingRequestSlot))
        } throws ResourceAlreadyExistsException.builder().build() andThen
            CompletableFuture.completedFuture(CreateThingResponse.builder().build()) andThenThrows
            ResourceAlreadyExistsException.builder().build() andThen
            CompletableFuture.completedFuture(CreateThingResponse.builder().build())
        // Here we mock the deleteThing will raise exception as failure
        every {
            ioTClient.deleteThing(capture(deleteThingRequest))
        } throws InvalidRequestException.builder().build()
        // Even though re-creation didn't go through, we still have things available.
        runBlockingTest {
            iotThingManager.createAndStoreThings(simulationMapping)
        }
        // We mock deleteThing always fail and there are 10 retries for each deleteThing. Hence total is 2 * 10
        Assertions.assertEquals(
            20,
            deleteThingRequest.map {
                val builder = DeleteThingRequest.builder()
                it.accept(builder)
                builder.build().thingName()
            }.size
        )
        // The deletion didn't go through, so overall createThing API call should match with number of things
        Assertions.assertEquals(
            carList.size,
            createThingRequestSlot.map {
                val builder = CreateThingRequest.builder()
                it.accept(builder)
                builder.build().thingName()
            }.size
        )
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
        val result = runBlocking { iotThingManager.deleteThings(simulationMapping, deletePolicy = true, deleteCert = true) }
        Assertions.assertTrue(carList == result.successList)
        Assertions.assertEquals(0, result.failedList.size)
        Assertions.assertTrue(
            carList ==
                listThingPrincipalRequestSlot.map {
                    val builder = ListThingPrincipalsRequest.builder()
                    it.accept(builder)
                    builder.build().thingName()
                }.toSet()
        )
        Assertions.assertTrue(
            carList ==
                detachThingPrincipalRequest.map {
                    val builder = DetachThingPrincipalRequest.builder()
                    it.accept(builder)
                    builder.build().thingName()
                }.toSet()
        )
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
        Assertions.assertTrue(
            carList ==
                deleteCertificateRequest.map {
                    val builder = DeleteCertificateRequest.builder()
                    it.accept(builder)
                    builder.build().certificateId()
                }.toSet()
        )
        Assertions.assertTrue(
            carList ==
                deleteThingRequest.map {
                    val builder = DeleteThingRequest.builder()
                    it.accept(builder)
                    builder.build().thingName()
                }.toSet()
        )
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
    fun `When deleteThings called with listThingPrincipals returns multiple pages of certificates`() {
        // Here we mock listThingPrincipals returns multiple page of principals
        every {
            ioTClient.listThingPrincipals(capture(listThingPrincipalRequestSlot))
        } returns CompletableFuture.completedFuture(
            ListThingPrincipalsResponse.builder().principals(listOf("principal")).nextToken("nextToken").build()
        ) andThen
            CompletableFuture.completedFuture(
                ListThingPrincipalsResponse.builder().principals(listOf("principal")).nextToken(null).build()
            )
        val result = runBlocking { iotThingManager.deleteThings(simulationMapping) }
        Assertions.assertEquals(carList, result.successList)
        Assertions.assertEquals(0, result.failedList.size)
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
        unmockkConstructor(ResourceNotFoundException::class)
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
    fun `When deleteThings called with listTargetsForPolicy returns multiple pages of certificates`() {
        every {
            ioTClient.listTargetsForPolicy(capture(listTargetsForPolicyRequest))
        } returns CompletableFuture.completedFuture(
            ListTargetsForPolicyResponse.builder().targets(listOf("cert")).nextMarker("nextMarker").build()
        ) andThen
            CompletableFuture.completedFuture(
                ListTargetsForPolicyResponse.builder().targets(listOf("cert")).build()
            )
        val result = runBlocking { iotThingManager.deleteThings(simulationMapping, deletePolicy = true) }
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
        runBlockingTest {
            iotThingManager.deleteThings(simulationMapping, deletePolicy = true)
        }
    }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun `When deleteThings called with iotClient deleteCertificate failed all the retries`() {
        every {
            ioTClient.deleteCertificate(capture(deleteCertificateRequest))
        } throws DeleteConflictException.builder().build()
        assertThrows<CertificateDeletionException> {
            runBlockingTest {
                iotThingManager.deleteThings(simulationMapping, deletePolicy = true, deleteCert = true)
            }
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

    @Test
    fun `When handing edgeUploadS3BucketName createRoleAndAlias should create role and alias`() {
        val roleArn = "arn:aws:iam::12345678901:role/" + IoTThingManager.DEFAULT_RICH_DATA_ROLE_NAME
        every { iamClient.createRole(any<Consumer<CreateRoleRequest.Builder>>()) } returns CreateRoleResponse.builder().role(
            Role.builder().arn(roleArn).build()
        ).build()
        every { iamClient.putRolePolicy(any<Consumer<PutRolePolicyRequest.Builder>>()) } returns PutRolePolicyResponse.builder().build()
        every { ioTClient.createRoleAlias(any<Consumer<CreateRoleAliasRequest.Builder>>()) } returns CompletableFuture.completedFuture(
            CreateRoleAliasResponse.builder()
                .roleAliasArn("arn:aws:iot:us-west-2:12345678901:rolealias/UnitTestRoleAlias").build()
        )

        runBlocking(Dispatchers.IO) {
            iotThingManager.createAndStoreThings(simulationMapping, recreatePolicyIfAlreadyExists = true, edgeUploadS3BucketName = "edgeUnitTestBucketName")
        }

        coVerify(exactly = 1) {
            iamClient.createRole(
                withArg<Consumer<CreateRoleRequest.Builder>> {
                    val builder = CreateRoleRequest.builder()
                    it.accept(builder)
                    val createRoleRequest = builder.build()
                    Assertions.assertEquals(createRoleRequest.roleName(), IoTThingManager.DEFAULT_RICH_DATA_ROLE_NAME)
                }
            )
            iamClient.putRolePolicy(
                withArg<Consumer<PutRolePolicyRequest.Builder>> {
                    val builder = PutRolePolicyRequest.builder()
                    it.accept(builder)
                    val putRolePolicyRequest = builder.build()
                    Assertions.assertEquals(putRolePolicyRequest.roleName(), IoTThingManager.DEFAULT_RICH_DATA_ROLE_NAME)
                    Assertions.assertEquals(putRolePolicyRequest.policyDocument(), getIamPolicy("edgeUnitTestBucketName"))
                }
            )
            ioTClient.createRoleAlias(
                withArg<Consumer<CreateRoleAliasRequest.Builder>> {
                    val builder = CreateRoleAliasRequest.builder()
                    it.accept(builder)
                    val createRoleAliasRequest = builder.build()
                    Assertions.assertEquals(createRoleAliasRequest.roleAlias(), IoTThingManager.DEFAULT_RICH_DATA_ROLE_ALIAS)
                    Assertions.assertEquals(createRoleAliasRequest.roleArn(), roleArn)
                }
            )
        }
    }

    @Test
    fun `When createRoleAndAlias should reuse existing role and alias if they already exist`() {
        val roleArn = "arn:aws:iam::12345678901:role/" + IoTThingManager.DEFAULT_RICH_DATA_ROLE_NAME
        every { iamClient.createRole(any<Consumer<CreateRoleRequest.Builder>>()) } throws EntityAlreadyExistsException.builder().build()
        every { iamClient.getRole(any<Consumer<GetRoleRequest.Builder>>()) } returns GetRoleResponse.builder().role(
            Role.builder().arn(roleArn).build()
        ).build()
        every { iamClient.putRolePolicy(any<Consumer<PutRolePolicyRequest.Builder>>()) } returns PutRolePolicyResponse.builder().build()
        every { ioTClient.createRoleAlias(any<Consumer<CreateRoleAliasRequest.Builder>>()) } throws ResourceAlreadyExistsException.builder().build()
        every { ioTClient.describeRoleAlias(any<Consumer<DescribeRoleAliasRequest.Builder>>()) } returns CompletableFuture.completedFuture(
            DescribeRoleAliasResponse.builder().roleAliasDescription(
                RoleAliasDescription.builder()
                    .roleAliasArn("arn:aws:iot:us-west-2:12345678901:rolealias/UnitTestRoleAlias").build()
            ).build()
        )

        runBlocking(Dispatchers.IO) {
            iotThingManager.createAndStoreThings(simulationMapping, recreatePolicyIfAlreadyExists = true, edgeUploadS3BucketName = "edgeUnitTestBucketName")
        }

        coVerify(exactly = 1) {
            iamClient.createRole(
                withArg<Consumer<CreateRoleRequest.Builder>> {
                    val builder = CreateRoleRequest.builder()
                    it.accept(builder)
                    val createRoleRequest = builder.build()
                    Assertions.assertEquals(createRoleRequest.roleName(), IoTThingManager.DEFAULT_RICH_DATA_ROLE_NAME)
                }
            )
            iamClient.getRole(
                withArg<Consumer<GetRoleRequest.Builder>> {
                    val builder = GetRoleRequest.builder()
                    it.accept(builder)
                    val getRoleRequest = builder.build()
                    Assertions.assertEquals(getRoleRequest.roleName(), IoTThingManager.DEFAULT_RICH_DATA_ROLE_NAME)
                }
            )
            iamClient.putRolePolicy(
                withArg<Consumer<PutRolePolicyRequest.Builder>> {
                    val builder = PutRolePolicyRequest.builder()
                    it.accept(builder)
                    val putRolePolicyRequest = builder.build()
                    Assertions.assertEquals(putRolePolicyRequest.roleName(), IoTThingManager.DEFAULT_RICH_DATA_ROLE_NAME)
                    Assertions.assertEquals(putRolePolicyRequest.policyDocument(), getIamPolicy("edgeUnitTestBucketName"))
                }
            )
            ioTClient.createRoleAlias(
                withArg<Consumer<CreateRoleAliasRequest.Builder>> {
                    val builder = CreateRoleAliasRequest.builder()
                    it.accept(builder)
                    val createRoleAliasRequest = builder.build()
                    Assertions.assertEquals(createRoleAliasRequest.roleAlias(), IoTThingManager.DEFAULT_RICH_DATA_ROLE_ALIAS)
                    Assertions.assertEquals(createRoleAliasRequest.roleArn(), roleArn)
                }
            )
            ioTClient.describeRoleAlias(
                withArg<Consumer<DescribeRoleAliasRequest.Builder>> {
                    val builder = DescribeRoleAliasRequest.builder()
                    it.accept(builder)
                    val createRoleAliasRequest = builder.build()
                    Assertions.assertEquals(createRoleAliasRequest.roleAlias(), IoTThingManager.DEFAULT_RICH_DATA_ROLE_ALIAS)
                }
            )
        }
    }
}
