package com.amazonaws.iot.fleetwise.vehiclesimulator.cert

import com.amazonaws.iot.fleetwise.vehiclesimulator.SimulationMetaData
import com.amazonaws.iot.fleetwise.vehiclesimulator.VehicleSetupStatus
import com.amazonaws.iot.fleetwise.vehiclesimulator.storage.S3Storage
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.acmpca.AcmPcaAsyncClient
import software.amazon.awssdk.services.acmpca.model.GetCertificateAuthorityCertificateRequest
import software.amazon.awssdk.services.acmpca.model.GetCertificateRequest
import software.amazon.awssdk.services.acmpca.model.IssueCertificateRequest
import software.amazon.awssdk.services.acmpca.model.SigningAlgorithm
import software.amazon.awssdk.services.acmpca.model.Validity
import software.amazon.awssdk.services.acmpca.model.ValidityPeriodType
import java.io.StringWriter
import java.security.KeyPair
import java.security.KeyPairGenerator

/**
 * Certificate and private key in PEM format
 */
data class CertificateAndPrivateKey(
    val privateKey: String,
    val certificatePem: String
)

class ACMCertificateManager(
    private val acmPcaClient: AcmPcaAsyncClient,
    private val s3Storage: S3Storage
) {

    private val log = LoggerFactory.getLogger(ACMCertificateManager::class.java)

    suspend fun setupVehiclesWithCertAndPrivateKey(
        simConfigMap: List<SimulationMetaData>
    ): VehicleSetupStatus = supervisorScope {
        val successVehicles = mutableSetOf<String>()

        simConfigMap.chunked(CREATE_CERT_BATCH_SIZE).forEach { simInputList ->
            val jobs = simInputList.map { simInput ->
                async {
                    // certificate code goes here
                    val deviceCertConfig = simInput.deviceCertificateConfig!!

                    log.info("Creating private key and device certificate for vehicle: ${simInput.vehicleId}")
                    val certAndPrivateKey = createDeviceCertWithCaCertAndPrivateKey(
                        deviceCertConfig.pcaArn,
                        deviceCertConfig.commonName,
                        deviceCertConfig.organization,
                        deviceCertConfig.countryCode,
                        deviceCertConfig.validityDays
                    )
                    s3Storage.put(simInput.s3.bucket, "${simInput.s3.key}/cert.crt", certAndPrivateKey.certificatePem.toByteArray())
                    s3Storage.put(simInput.s3.bucket, "${simInput.s3.key}/pri.key", certAndPrivateKey.privateKey.toByteArray())
                    simInput.vehicleId
                }
            }

            jobs.forEach { job ->
                try {
                    successVehicles.add(job.await())
                } catch (ex: Exception) {
                    log.error("Failed to create certificate", ex)
                }
            }
        }
        return@supervisorScope VehicleSetupStatus(successVehicles, simConfigMap.map { it.vehicleId }.toSet() - successVehicles)
    }

    fun createDeviceCertWithCaCertAndPrivateKey(
        caArn: String,
        commonName: String,
        organization: String,
        countryCode: String,
        expirationDurationDays: Long = 120
    ): CertificateAndPrivateKey {

        // create a private key pair
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()
        log.info("Created Private and Public Key Pair using RSA algorithm with 2048 bits.")

        // create a certificate signing request
        log.info("Creating CSR for device certificate")
        val csr = createCsr(keyPair, commonName, organization, countryCode)
        val csrBytes = SdkBytes.fromByteArray(csr.toByteArray(Charsets.UTF_8))

        log.info("CSR Generated. Issuing device certificate with CA ARN: $caArn")
        val issueCertificateResponse = acmPcaClient.issueCertificate(
            IssueCertificateRequest.builder()
                .certificateAuthorityArn(caArn)
                .csr(csrBytes)
                .signingAlgorithm(SigningAlgorithm.SHA256_WITHRSA)
                .validity(
                    Validity.builder()
                        .value(expirationDurationDays)
                        .type(ValidityPeriodType.DAYS)
                        .build()
                )
                .build()
        ).get()

        val deviceCertArn = issueCertificateResponse.certificateArn()
        log.info("Waiting for device certificate to be issued, with ARN: $deviceCertArn")

        val waiter = acmPcaClient.waiter()
        val getCertificateRequest: GetCertificateRequest? = GetCertificateRequest.builder()
            .certificateAuthorityArn(caArn)
            .certificateArn(deviceCertArn)
            .build()

        waiter.waitUntilCertificateIssued(getCertificateRequest).get()
        log.info("Device Certificate has been issued, with ARN: $deviceCertArn")

        val deviceCert = acmPcaClient.getCertificate(getCertificateRequest).get().certificate()
        log.info("Device Certificate has been retrieved, with ARN: $deviceCertArn")

        log.info("Retrieving CA Certificate with CA ARN: $caArn")
        val caCert = acmPcaClient.getCertificateAuthorityCertificate(
            GetCertificateAuthorityCertificateRequest.builder()
                .certificateAuthorityArn(caArn)
                .build()
        ).get().certificate()
        log.info("CA Certificate has been retrieved, with CA ARN: $caArn")

        // Concatenate device cert and ca cert in a single file
        val certificatePem = "$deviceCert\n$caCert"
        log.info("Returning device certificate with CA Certificate. Number of bytes: ${certificatePem.length}")

        val stringWriter = StringWriter()
        val pemObject = PemObject("RSA PRIVATE KEY", keyPair.private.encoded)
        val pemWriter = PemWriter(stringWriter)
        pemWriter.writeObject(pemObject)
        pemWriter.close()

        return CertificateAndPrivateKey(
            privateKey = stringWriter.toString(),
            certificatePem = certificatePem
        )
    }

    fun createCsr(
        keyPair: KeyPair,
        commonName: String,
        organization: String,
        countryCode: String,
    ): String {
        val csrBuilder = JcaPKCS10CertificationRequestBuilder(
            X500Name("CN=$commonName, O=$organization, C=$countryCode"),
            keyPair.public
        )

        // Sign the CSR
        val csBuilder = JcaContentSignerBuilder("SHA256withRSA")
        val contentSigner = csBuilder.build(keyPair.private)
        val csr = csrBuilder.build(contentSigner)

        // Convert CSR to PEM format
        val stringWriter = StringWriter()
        val pemObject = PemObject("CERTIFICATE REQUEST", csr.encoded)
        val pemWriter = PemWriter(stringWriter)
        pemWriter.writeObject(pemObject)
        pemWriter.close()
        return stringWriter.toString()
    }

    companion object {
        const val CREATE_CERT_BATCH_SIZE = 10
    }
}
