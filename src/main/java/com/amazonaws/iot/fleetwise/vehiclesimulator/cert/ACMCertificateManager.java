package com.amazonaws.iot.fleetwise.vehiclesimulator.cert;

import com.amazonaws.iot.fleetwise.vehiclesimulator.DeviceCertificateConfig;
import com.amazonaws.iot.fleetwise.vehiclesimulator.SimulationMetaData;
import com.amazonaws.iot.fleetwise.vehiclesimulator.VehicleSetupStatus;
import com.amazonaws.iot.fleetwise.vehiclesimulator.storage.S3Storage;
import kotlin.text.Charsets;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.acmpca.AcmPcaAsyncClient;
import software.amazon.awssdk.services.acmpca.model.*;
import software.amazon.awssdk.services.acmpca.waiters.AcmPcaAsyncWaiter;

import java.io.IOException;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ACMCertificateManager {
    private final AcmPcaAsyncClient acmPcaClient;
    private final S3Storage s3Storage;

    public static final int CREATE_CERT_BATCH_SIZE = 10;

    private final Logger log = LoggerFactory.getLogger(ACMCertificateManager.class);

    public ACMCertificateManager(AcmPcaAsyncClient acmPcaClient, S3Storage s3Storage){
        this.acmPcaClient = acmPcaClient;
        this.s3Storage = s3Storage;
    }

    public VehicleSetupStatus setupVehiclesWithCertAndPrivateKey(List<SimulationMetaData> simConfigMap) {
        {
            Set<String> successVehicles = new HashSet<>();
            AtomicInteger counter = new AtomicInteger();
            Map<Integer, List<SimulationMetaData>> listOfChunks = simConfigMap.stream()
                    .collect(Collectors.groupingBy(it -> counter.getAndIncrement() / CREATE_CERT_BATCH_SIZE));
            listOfChunks.forEach((key, value) -> {
                List<CompletableFuture<String>> jobs = new ArrayList<>();
                value.forEach(
                        simInput -> {
                            CompletableFuture<String> cf =
                                    CompletableFuture.supplyAsync(() ->
                                    {
                                        DeviceCertificateConfig deviceCertConfig = simInput.getDeviceCertificateConfig();
                                        log.info("Creating private key and device certificate for vehicle: {}", simInput.getVehicleId());
                                        CertificateAndPrivateKey certAndPrivateKey = createDeviceCertWithCaCertAndPrivateKey(
                                                deviceCertConfig.getPcaArn(),
                                                deviceCertConfig.getCommonName(),
                                                deviceCertConfig.getOrganization(),
                                                deviceCertConfig.getCountryCode(),
                                                deviceCertConfig.getValidityDays()
                                        );
                                        s3Storage.put(simInput.getS3().getBucket(), simInput.getS3().getKey() + "/cert.crt", certAndPrivateKey.getCertificatePem().getBytes());
                                        s3Storage.put(simInput.getS3().getBucket(), simInput.getS3().getKey() + "/pri.key", certAndPrivateKey.getPrivateKey().getBytes());
                                        return simInput.getVehicleId();
                                    });
                            jobs.add(cf);
                        }
                );
                jobs.forEach(job -> {
                            try {
                                successVehicles.add(job.get());
                            } catch (InterruptedException | ExecutionException e) {
                                throw new RuntimeException(e);
                            }
                        }
                );
            });
            Set<String> failedVehicles = simConfigMap.stream().map(SimulationMetaData::getVehicleId).collect(Collectors.toSet());
            failedVehicles.removeAll(successVehicles);
            return new VehicleSetupStatus(successVehicles, failedVehicles);
        }
    }

    private CertificateAndPrivateKey createDeviceCertWithCaCertAndPrivateKey(String caArn,
            String commonName,
             String organization,
             String countryCode,
             Long expirationDurationDays) {
        if(expirationDurationDays == null){
            expirationDurationDays = 120L;
        }
        // create a private key pair
        KeyPairGenerator keyPairGenerator = null;
        try {
            keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        log.info("Created Private and Public Key Pair using RSA algorithm with 2048 bits.");

        // create a certificate signing request
        log.info("Creating CSR for device certificate");
        String csr = createCsr(keyPair, commonName, organization, countryCode);
        SdkBytes csrBytes = SdkBytes.fromByteArray(csr.getBytes(Charsets.UTF_8));

        log.info("CSR Generated. Issuing device certificate with CA ARN: {}", caArn);
        CompletableFuture<IssueCertificateResponse> issueCertificateResponseFuture = acmPcaClient.issueCertificate(
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
        );
        IssueCertificateResponse issueCertificateResponse = null;
        try {
            issueCertificateResponse = issueCertificateResponseFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        String deviceCertArn = issueCertificateResponse.certificateArn();
        log.info("Waiting for device certificate to be issued, with ARN: {}", deviceCertArn);

        AcmPcaAsyncWaiter waiter = acmPcaClient.waiter();
        GetCertificateRequest getCertificateRequest = GetCertificateRequest.builder()
                .certificateAuthorityArn(caArn)
                .certificateArn(deviceCertArn)
                .build();

        try {
            waiter.waitUntilCertificateIssued(getCertificateRequest).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        log.info("Device Certificate has been issued, with ARN: {}", deviceCertArn);

        String deviceCert = null;
        try {
            deviceCert = acmPcaClient.getCertificate(getCertificateRequest).get().certificate();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        log.info("Device Certificate has been retrieved, with ARN: {}", deviceCertArn);

        log.info("Retrieving CA Certificate with CA ARN: {}", caArn);
        String caCert = "";
        try {
            caCert = acmPcaClient.getCertificateAuthorityCertificate(
                    GetCertificateAuthorityCertificateRequest.builder()
                            .certificateAuthorityArn(caArn)
                            .build()
            ).get().certificate();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        log.info("CA Certificate has been retrieved, with CA ARN: {}", caArn);

        // Concatenate device cert and ca cert in a single file
        String certificatePem = deviceCert +"\n" + caCert;
        log.info("Returning device certificate with CA Certificate. Number of bytes: {}", certificatePem.length());

        StringWriter stringWriter = new StringWriter();
        PemObject pemObject = new PemObject("RSA PRIVATE KEY", keyPair.getPrivate().getEncoded());
        PemWriter pemWriter = new PemWriter(stringWriter);
        try {
            pemWriter.writeObject(pemObject);
            pemWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new CertificateAndPrivateKey(stringWriter.toString(),certificatePem);
    }

    private String createCsr(
            KeyPair keyPair,
            String commonName,
            String organization,
            String countryCode)
    {
        String input = "CN=" + commonName+ ", O=" + organization + ", C=" +countryCode;
        JcaPKCS10CertificationRequestBuilder csrBuilder = new JcaPKCS10CertificationRequestBuilder(
                new X500Name(input),
                keyPair.getPublic()
        );

        // Sign the CSR
        JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder("SHA256withRSA");
        ContentSigner contentSigner = null;
        try {
            contentSigner = csBuilder.build(keyPair.getPrivate());
        } catch (OperatorCreationException e) {
            throw new RuntimeException(e);
        }
        PKCS10CertificationRequest csr = csrBuilder.build(contentSigner);

        // Convert CSR to PEM format
        StringWriter stringWriter = new StringWriter();
        PemObject pemObject = null;
        try {
            pemObject = new PemObject("CERTIFICATE REQUEST", csr.getEncoded());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        PemWriter pemWriter = new PemWriter(stringWriter);
        try {
            pemWriter.writeObject(pemObject);
            pemWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return stringWriter.toString();
    }

}