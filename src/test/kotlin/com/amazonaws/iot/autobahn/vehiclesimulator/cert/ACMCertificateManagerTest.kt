package com.amazonaws.iot.autobahn.vehiclesimulator.cert

import com.amazonaws.iot.autobahn.vehiclesimulator.storage.S3Storage
import com.amazonaws.iot.autobahn.vehiclesimulator.utils.TestUtils.caArn
import com.amazonaws.iot.autobahn.vehiclesimulator.utils.TestUtils.commonName
import com.amazonaws.iot.autobahn.vehiclesimulator.utils.TestUtils.countryCode
import com.amazonaws.iot.autobahn.vehiclesimulator.utils.TestUtils.createSimulationInput
import com.amazonaws.iot.autobahn.vehiclesimulator.utils.TestUtils.organization
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.core.internal.waiters.DefaultWaiterResponse
import software.amazon.awssdk.services.acmpca.AcmPcaAsyncClient
import software.amazon.awssdk.services.acmpca.model.GetCertificateAuthorityCertificateRequest
import software.amazon.awssdk.services.acmpca.model.GetCertificateAuthorityCertificateResponse
import software.amazon.awssdk.services.acmpca.model.GetCertificateRequest
import software.amazon.awssdk.services.acmpca.model.GetCertificateResponse
import software.amazon.awssdk.services.acmpca.model.IssueCertificateRequest
import software.amazon.awssdk.services.acmpca.model.IssueCertificateResponse
import software.amazon.awssdk.services.acmpca.model.SigningAlgorithm
import software.amazon.awssdk.services.acmpca.model.Validity
import software.amazon.awssdk.services.acmpca.model.ValidityPeriodType
import software.amazon.awssdk.services.acmpca.waiters.AcmPcaAsyncWaiter
import java.io.StringReader
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.concurrent.CompletableFuture

class ACMCertificateManagerTest {

    companion object {
        val mockDeviceCertificate =
            """
        -----BEGIN CERTIFICATE-----
        MIIDWTCCAkGgAwIBAgIUXJQ3Z3Z3Z3Z3Z3Z3Z3Z3Z3Z3Z3YwDQYJKoZIhvcNAQEL
        BQAwFjEUMBIGA1UEAxMLY2Etc2VydmVyLTAeFw0yMTA0MjQxNjQyMjNaFw0yMTA0
        MjQxNjQyMjNaMBYxFDASBgNVBAMTC2NhLXNlcnZlci0wggEiMA0GCSqGSIb3DQEB
        AQUAA4IBDw
        -----END CERTIFICATE-----
        """
    }

    private val acmPcaClient = mockk<AcmPcaAsyncClient>()
    private val acmPcaAsyncWaiter = mockk<AcmPcaAsyncWaiter>()
    private val s3Storage = mockk<S3Storage>()
    private val acmCertificateManager: ACMCertificateManager

    private val certificateArn = "arn:aws:acm-pca:us-east-1:123456789012:certificate-authority/12345678-1234-1234-1234-123456789012/certificate/12345678-1234-1234-1234-123456789012"

    private val keyPair: KeyPair
    private val issueCertificateRequest: IssueCertificateRequest
    private val getCertificateRequest: GetCertificateRequest
    private val getCaCertificateRequest: GetCertificateAuthorityCertificateRequest
    private val csrBytes: SdkBytes
    private val expirationDays = 120L

    private val mockCACertificate =
        """
        -----BEGIN CERTIFICATE-----
        MIIDWTCCAkGgAwIBAgIUXJQ3Z3Z3Z3Z3Z3Z3Z3Z3Z3Z3Z3YwDQYJKoZIhvcNAQEL
        BQAwFjEUMBIGA1UEAxMLY2Etc2VydmVyLTAeFw0yMTA0MjQxNjQyMjNaFw0yMTA0
        MjQxNjQyMjNaMBYxFjhgygNVBAMTC2NhLXNlcnZlci0wggEiMA0GCSqGSIb3DQEB
        AQUAA4IBDw
        -----END CERTIFICATE-----
        """

    init {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        keyPair = keyPairGenerator.generateKeyPair()
        acmCertificateManager = ACMCertificateManager(acmPcaClient, s3Storage)

        val csr = acmCertificateManager.createCsr(
            keyPair,
            commonName,
            organization,
            countryCode
        )
        csrBytes = SdkBytes.fromByteArray(csr.toByteArray())

        issueCertificateRequest = IssueCertificateRequest.builder()
            .certificateAuthorityArn(caArn)
            .csr(csrBytes)
            .signingAlgorithm(SigningAlgorithm.SHA256_WITHRSA)
            .validity(
                Validity.builder()
                    .type(ValidityPeriodType.DAYS)
                    .value(expirationDays)
                    .build()
            )
            .build()

        getCertificateRequest = GetCertificateRequest.builder()
            .certificateArn(certificateArn)
            .certificateAuthorityArn(caArn)
            .build()

        getCaCertificateRequest = GetCertificateAuthorityCertificateRequest.builder()
            .certificateAuthorityArn(caArn)
            .build()

        every { acmPcaClient.waiter() } returns acmPcaAsyncWaiter
    }

    fun MockKMatcherScope.issueCertificateRequestEq(req: IssueCertificateRequest) = match<IssueCertificateRequest> {
        it.certificateAuthorityArn() == req.certificateAuthorityArn() &&
            it.signingAlgorithm() == req.signingAlgorithm() &&
            it.validity() == req.validity()
    }

    fun MockKMatcherScope.getCertificateRequestEq(req: GetCertificateRequest) = match<GetCertificateRequest> {
        it.certificateAuthorityArn() == req.certificateAuthorityArn() &&
            it.certificateArn() == req.certificateArn()
    }

    fun MockKMatcherScope.getCaCertificateRequestEq(req: GetCertificateAuthorityCertificateRequest) = match<GetCertificateAuthorityCertificateRequest> {
        it.certificateAuthorityArn() == req.certificateAuthorityArn()
    }

    @Test
    fun `when createCsr is called with valid parameters, then a valid CSR is created`() {
        val csr = acmCertificateManager.createCsr(
            keyPair,
            commonName,
            organization,
            countryCode
        )
        assertTrue(csr.isNotEmpty())

        val pemParser = PEMParser(StringReader(csr))
        val csrPemObject = pemParser.readObject() as PKCS10CertificationRequest
        assertTrue(csrPemObject.subject.toString().contains(commonName))
        assertTrue(csrPemObject.subject.toString().contains(organization))
        assertTrue(csrPemObject.subject.toString().contains(countryCode))
        assertTrue(csrPemObject.isSignatureValid(JcaContentVerifierProviderBuilder().build(keyPair.public)))
    }

    @Test
    fun `when createDeviceCertWithCaCertAndPrivateKey is called with valid parameters, then a certificate and private key are created`() {
        mockAllACMPCACalls()

        val privateKeyAndCertificate = acmCertificateManager.createDeviceCertWithCaCertAndPrivateKey(
            caArn,
            commonName,
            organization,
            countryCode,
            expirationDays
        )
        val expectedCertificate = "$mockDeviceCertificate\n$mockCACertificate"
        assertTrue(privateKeyAndCertificate.privateKey.isNotBlank())
        assertEquals(expectedCertificate, privateKeyAndCertificate.certificatePem)
    }

    private fun mockAllACMPCACalls() {
        val getCertificateResponse = GetCertificateResponse.builder().certificate(mockDeviceCertificate).build()

        every {
            acmPcaAsyncWaiter.waitUntilCertificateIssued(getCertificateRequestEq(getCertificateRequest))
        } returns CompletableFuture.completedFuture(
            DefaultWaiterResponse.builder<GetCertificateResponse>()
                .attemptsExecuted(1)
                .response(getCertificateResponse)
                .build()
        )

        every {
            acmPcaClient.issueCertificate(issueCertificateRequestEq(issueCertificateRequest))
        } returns CompletableFuture.completedFuture(
            IssueCertificateResponse.builder().certificateArn(certificateArn).build()
        )

        every {
            acmPcaClient.getCertificate(getCertificateRequestEq(getCertificateRequest))
        } returns CompletableFuture.completedFuture(
            getCertificateResponse
        )

        every {
            acmPcaClient.getCertificateAuthorityCertificate(getCaCertificateRequestEq(getCaCertificateRequest))
        } returns CompletableFuture.completedFuture(
            GetCertificateAuthorityCertificateResponse.builder().certificate(mockCACertificate).build()
        )
    }

    @Test
    fun `when setupVehiclesWithCertAndPrivateKey is called with 5 vehicles and all succeed, we get a success result for all input`() {
        mockAllACMPCACalls()

        coEvery { s3Storage.put(any(), any(), any()) } returns Unit

        val numVehicles = 5
        val simulationInput = createSimulationInput(5, false)

        val vehicleSetupStatus = runBlocking {
            acmCertificateManager.setupVehiclesWithCertAndPrivateKey(simulationInput)
        }

        coVerify(exactly = (numVehicles * 2)) { s3Storage.put(any(), any(), any()) }
        verify(exactly = numVehicles) { acmPcaClient.issueCertificate(issueCertificateRequestEq(issueCertificateRequest)) }
        verify(exactly = numVehicles) { acmPcaClient.getCertificate(getCertificateRequestEq(getCertificateRequest)) }
        verify(exactly = numVehicles) { acmPcaClient.getCertificateAuthorityCertificate(getCaCertificateRequestEq(getCaCertificateRequest)) }

        assertEquals(numVehicles, vehicleSetupStatus.successList.size)
        assertEquals(0, vehicleSetupStatus.failedList.size)
    }

    @Test
    fun `when setupVehiclesWithCertAndPrivateKey is called with 25 vehicles and all succeed, we get a success result for all input`() {
        mockAllACMPCACalls()

        coEvery { s3Storage.put(any(), any(), any()) } returns Unit

        val numVehicles = 25
        val simulationInput = createSimulationInput(numVehicles, false)

        val vehicleSetupStatus = runBlocking {
            acmCertificateManager.setupVehiclesWithCertAndPrivateKey(simulationInput)
        }

        coVerify(exactly = (numVehicles * 2)) { s3Storage.put(any(), any(), any()) }
        verify(exactly = numVehicles) { acmPcaClient.issueCertificate(issueCertificateRequestEq(issueCertificateRequest)) }
        verify(exactly = numVehicles) { acmPcaClient.getCertificate(getCertificateRequestEq(getCertificateRequest)) }
        verify(exactly = numVehicles) { acmPcaClient.getCertificateAuthorityCertificate(getCaCertificateRequestEq(getCaCertificateRequest)) }

        assertEquals(numVehicles, vehicleSetupStatus.successList.size)
        assertEquals(0, vehicleSetupStatus.failedList.size)
    }

    @Test
    fun `when setupVehiclesWithCertAndPrivateKey is called and fails for some of the vehicles, we get a success result for some and failresult for others`() {
        mockAllACMPCACalls()
        coEvery { s3Storage.put(any(), any(), any()) } returns Unit

        val numVehicles = 25
        val maxFailCalls = 3
        val numSuccessCalls = numVehicles - maxFailCalls
        val vehicles = createSimulationInput(numVehicles, false)

        every {
            acmPcaClient.issueCertificate(any<IssueCertificateRequest>())
        } answers {
            throw AwsServiceException.builder().build()
        } andThen {
            throw AwsServiceException.builder().build()
        } andThen {
            throw AwsServiceException.builder().build()
        } andThen {
            CompletableFuture.completedFuture(
                IssueCertificateResponse.builder().certificateArn(certificateArn).build()
            )
        }

        val vehicleSetupStatus = runBlocking {
            acmCertificateManager.setupVehiclesWithCertAndPrivateKey(vehicles)
        }
        coVerify(exactly = (numSuccessCalls * 2)) { s3Storage.put(any(), any(), any()) }
        verify(exactly = numVehicles) { acmPcaClient.issueCertificate(issueCertificateRequestEq(issueCertificateRequest)) }
        verify(exactly = numSuccessCalls) { acmPcaClient.getCertificate(getCertificateRequestEq(getCertificateRequest)) }
        verify(exactly = numSuccessCalls) { acmPcaClient.getCertificateAuthorityCertificate(getCaCertificateRequestEq(getCaCertificateRequest)) }

        assertEquals(numSuccessCalls, vehicleSetupStatus.successList.size)
        assertEquals(maxFailCalls, vehicleSetupStatus.failedList.size)
    }
}
