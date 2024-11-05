package com.amazonaws.iot.fleetwise.vehiclesimulator.iot;

import com.amazonaws.iot.fleetwise.vehiclesimulator.SimulationMetaData;
import com.amazonaws.iot.fleetwise.vehiclesimulator.VehicleSetupStatus;
import com.amazonaws.iot.fleetwise.vehiclesimulator.exceptions.CertificateDeletionException;
import com.amazonaws.iot.fleetwise.vehiclesimulator.storage.S3Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Retryable;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.iot.IotAsyncClient;
import software.amazon.awssdk.services.iot.model.*;
import software.amazon.awssdk.services.iot.model.CreatePolicyRequest;
import software.amazon.awssdk.services.iot.model.DeleteConflictException;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class IoTThingManager {

    private final IotAsyncClient iotClient;
    private final S3Storage s3Storage;
    private final IamClient iamClient;

    public static String DEFAULT_RICH_DATA_ROLE_NAME = "vehicle-simulator-credentials-provider-s3";
    public static String DEFAULT_RICH_DATA_ROLE_ALIAS = "vehicle-simulator-credentials-provider";
    public static int CREATE_DELETE_THINGS_BATCH_SIZE = 10;
    public static String CERTIFICATE_FILE_NAME = "cert.crt";
    public static String PRIVATE_KEY_FILE_NAME = "pri.key";
    public static final String DEFAULT_RICH_DATA_IAM_ASSUME_ROLE_POLICY_DOCUMENT = "{\n\"Version\": \"2012-10-17\",\n\"Statement\": [\n{\n\"Effect\": \"Allow\",\n\"Principal\": {\n\"Service\" : \"credentials.iot.amazonaws.com\"\n},\n\"Action\": \"sts:AssumeRole\"\n}\n]\n}";

    private static final Logger log = LoggerFactory.getLogger(IoTThingManager.class);

    public IoTThingManager(IotAsyncClient iotClient, S3Storage s3Storage , IamClient iamClient ) {
        this.iotClient = iotClient;
        this.s3Storage = s3Storage;
        this.iamClient = iamClient;
    }

    private CreateKeysAndCertificateResponse createKeysAndCertificate() {
        log.info("Creating certificate");
        // create private / public keys and cert
        CreateKeysAndCertificateResponse cert = null;
        try {
            cert = iotClient.createKeysAndCertificate(CreateKeysAndCertificateRequest.builder().setAsActive(true).build()).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        return cert;
    }

    private String createRoleAndAlias(String iamRoleName, String assumeRolePolicyDocument,
            String policyDocument, String roleAlias) {
        log.info("Creating Iam role {} and IoT role alias for that role {}", iamRoleName, roleAlias);
        String iamRoleArn;
        try {
            iamRoleArn = iamClient.createRole(CreateRoleRequest.builder().roleName(iamRoleName).assumeRolePolicyDocument(assumeRolePolicyDocument).build())
                    .role().arn();
        } catch (EntityAlreadyExistsException ex) {
            log.info("Role {} already exists, keeping it", iamRoleName);
            iamRoleArn = iamClient.getRole(GetRoleRequest.builder().roleName(iamRoleName).build()).role().arn();
        }
        iamClient.putRolePolicy(PutRolePolicyRequest.builder().roleName(iamRoleName).policyName(iamRoleName).policyDocument(policyDocument).build());
        String roleAliasArn;
        try {
            roleAliasArn = iotClient.createRoleAlias(CreateRoleAliasRequest.builder().roleArn(iamRoleArn).credentialDurationSeconds(3600).roleAlias(roleAlias).build()).join().roleAliasArn();
        } catch (ResourceAlreadyExistsException ex) {
            log.info("Role alias {} already exists, keeping it", roleAlias);
            roleAliasArn = iotClient.describeRoleAlias(DescribeRoleAliasRequest.builder().roleAlias(roleAlias).build()).join().roleAliasDescription().roleAliasArn();
        }
        return roleAliasArn;
    }

    private void createPolicyAndAttachToCert(String policyName, String policyDocument,
            String certArn, Boolean recreatePolicyIfAlreadyExists) {
        // create policy
        try {
            log.info("Creating Policy {}", policyName);
            iotClient.createPolicy(CreatePolicyRequest.builder().policyName(policyName).policyDocument(policyDocument).build())
            .get();
        } catch (Exception ex) {
            if (recreatePolicyIfAlreadyExists && ex.getMessage().toLowerCase().contains("ResourceAlreadyExistsException".toLowerCase())) {
                log.info("Policy already exists, delete and create");
                deletePolicy(policyName);
                try {
                    iotClient.createPolicy(CreatePolicyRequest.builder().policyName(policyName).policyDocument(policyDocument).build())
                            .get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            } else {
                log.info("Policy already exists, reusing the same policy");
            }
        }
        log.info("Attaching policy {} to Cert {}", policyName, certArn);
        try {
            iotClient.attachPolicy(AttachPolicyRequest.builder().policyName(policyName)
                            .target(certArn).build()).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Retryable(retryFor = {ThrottlingException.class, ServiceUnavailableException.class}, maxAttempts=10)
    public void createThingAndAttachCert(String thingName, String certArn) {
        try {
            log.info("Creating Thing {}", thingName);
            // If this call is made multiple times using the same thing name and configuration, the call will succeed.
            // If this call is made with the same thing name but different configuration a ResourceAlreadyExistsException is thrown.
            iotClient.createThing(CreateThingRequest.builder().thingName(thingName).build())
            .get();
        } catch ( ResourceAlreadyExistsException ex) {
            log.info("Attempting to create existing Thing $thingName with different config, delete and re-create Thing");
            List<String> deletedThings = deleteThing(thingName);
            if (!deletedThings.isEmpty()) {
                try {
                    iotClient.createThing(CreateThingRequest.builder().thingName(thingName).build())
                            .get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            } else {
                // if deleteThing returns null, the deletion failed
                // We will reuse the thing instead of creating a new one
                log.error("Attempted to re-create thing {} but failed to delete it, reuse", thingName);
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("Attaching cert {} to Thing {}",certArn, thingName);
        try {
            iotClient.attachThingPrincipal(AttachThingPrincipalRequest.builder()
                    .thingName(thingName).principal(certArn).build()).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    // If policy no longer exit, this function will throw ResourceNotFoundException
    private void deletePolicy(String policyName) {
        // To delete policy, we need to detach it from the targets (certificate)
        String nextMarker = null;
        do {
            ListTargetsForPolicyResponse response = null;
            try {
                response = iotClient.listTargetsForPolicy(ListTargetsForPolicyRequest.builder().policyName(policyName).build())
                        .get();
                if(response != null) {
                    response.targets().forEach(it -> {
                        log.info("Detaching cert {}", it);
                        try {
                            iotClient.detachPolicy(DetachPolicyRequest.builder().policyName(policyName).target(it).build()).get();
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    nextMarker = response.nextMarker();
                }
            } catch (InterruptedException | ExecutionException e) {
                if(e.getMessage().toLowerCase().contains("ResourceNotFoundException".toLowerCase())){
                    log.warn("The policy does not exist: {}", policyName);
                } else{
                    throw new RuntimeException(e);
                }

            }
        } while (nextMarker != null);
        try {
            deletePolicyRetry(policyName);
        } catch (ExecutionException | InterruptedException e) {
            if(e.getMessage().toLowerCase().contains("ResourceNotFoundException".toLowerCase())){
                log.warn("The policy does not exist: {}", policyName);
            } else{
                throw new RuntimeException(e);
            }
        }
    }

    // Because of the distributed nature of Amazon Web Services, it can take up to
    // five minutes after a policy is detached before it's ready to be deleted.
    // We need retry if this happened
    @Retryable(retryFor = DeleteConflictException.class, maxAttempts=10)
    public void deletePolicyRetry(String policyName) throws ExecutionException, InterruptedException {
        log.info("Deleting policy {}", policyName);
        iotClient.deletePolicy(builder ->
                builder.policyName(policyName)).get();
    }

    private void deleteCerts(Set<String> principalSet) {
        principalSet.forEach(
            it -> {
                String certId = it.substring(it.lastIndexOf("/") + 1);
                log.info("De-activating cert {}", certId);
                try {
                    iotClient.updateCertificate (builder ->
                            builder.certificateId(certId).newStatus("INACTIVE").build()
                    ).get();
                    deleteCertRetry(it);
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        );
    }
    // The detachThingPrincipal is async call and might take few seconds to propagate.
    // So it might happen that cert is not ready to delete. If this happens, retry in a few moment
    // if it still failed to delete, we should log it as error but continue deleting the rest of things

    @Retryable(retryFor = DeleteConflictException.class, maxAttempts=10)
    public void deleteCertRetry(String certId) throws ExecutionException, InterruptedException {
        log.info("Deleting cert {}", certId);
        iotClient.deleteCertificate (builder ->
                builder.certificateId(certId).forceDelete(true).build()
        ).get();
    }

    private List<String> deleteThing(String thingName) {
        // First we query a list of thing principals for this Thing.
        List<String> principalList = new ArrayList<>();
        String nextToken = null;
        do {
            try {
                ListThingPrincipalsResponse response = iotClient.listThingPrincipals(ListThingPrincipalsRequest.builder().thingName(thingName).nextToken(nextToken).build()).get();
                principalList.addAll(response.principals());
                nextToken = response.nextToken();
            } catch (Exception e) {
                if(e.getMessage().toLowerCase().contains("ResourceNotFoundException".toLowerCase())){
                    // If exception raised due to Thing no longer exist, we catch the exception and continue deleting other things
                    log.warn("Thing $thingName does not exist: {}", thingName);
                    // Set the nextToken as null to exit the loop
                    nextToken = null;
                } else{
                    throw new RuntimeException(e);
                }
            }
        } while (nextToken != null);

        principalList.forEach(
                it -> {
                    log.info("Detaching cert {} from Thing {}", it, thingName);
                    try {
                        iotClient.detachThingPrincipal(builder ->
                                builder.thingName(thingName).principal(it).build())
                                .get();
                    } catch (InterruptedException | ExecutionException e) {
                        if(e.getMessage().toLowerCase().contains("ResourceNotFoundException".toLowerCase())){
                            log.info("ResourceNotFoundException {}", thingName);
                        } else{
                            throw new RuntimeException(e);
                        }
                    }
                }
        );
        deleteThingException(thingName);
        return principalList;
    }

    // Based on API doc, if thing doesn't exist, deleteThing still returns true
    // The previous call of detachThingPrincipal might take several seconds to propagate. Hence retry
    @Retryable(retryFor = InvalidRequestException.class, maxAttempts=10)
    public void deleteThingException(String thingName){
        log.info("Deleting Thing {}", thingName);
        try {
            iotClient.deleteThing(builder ->
                    builder.thingName(thingName).build()).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
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
    public VehicleSetupStatus createAndStoreThings(
            List<SimulationMetaData> simConfigMap, // Map<String, S3Storage.Companion.BucketAndKey>,
            String policyName,
            String policyDocument,
            Boolean  recreatePolicyIfAlreadyExists,
            String edgeUploadS3BucketName
    ) {
        CreateKeysAndCertificateResponse cert = createKeysAndCertificate();
        log.info("Certificate {} created", cert.certificateId());
        if (!edgeUploadS3BucketName.isEmpty()) {
            createRoleAndAlias(DEFAULT_RICH_DATA_ROLE_NAME, DEFAULT_RICH_DATA_IAM_ASSUME_ROLE_POLICY_DOCUMENT, getIamPolicy(edgeUploadS3BucketName), DEFAULT_RICH_DATA_ROLE_ALIAS);
        }
        createPolicyAndAttachToCert(policyName, policyDocument, cert.certificateArn(), recreatePolicyIfAlreadyExists);
        List<String> createdThings = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger();
        Map<Integer, List<SimulationMetaData>> listOfChunks = simConfigMap.stream()
                .collect(Collectors.groupingBy(it -> counter.getAndIncrement() / CREATE_DELETE_THINGS_BATCH_SIZE));
        listOfChunks.forEach((key, value) ->{
            // Create Things with batch size set as CREATE_DELETE_THINGS_BATCH_SIZE
            value.forEach(it -> {
                // We might run into throttling from IoT Core, hence use retry if throttle
                createThingAndAttachCert(it.getVehicleId(), cert.certificateArn());
                s3Storage.put(it.getS3().getBucket(), it.getS3().getKey() + "/cert.crt", cert.certificatePem().getBytes());
                s3Storage.put(it.getS3().getBucket(), it.getS3().getKey() + "/pri.key", cert.keyPair().privateKey().getBytes());
                log.info("Cert and Private Key will be stored at s3 bucket {} key: {}", it.getS3().getBucket(),it.getS3().getKey());
                createdThings.add(it.getVehicleId());
            });
        });

        List<String> list = simConfigMap.stream().map(SimulationMetaData::getVehicleId).collect(Collectors.toList());
        list.removeAll(createdThings);
        return new VehicleSetupStatus(new HashSet<>(createdThings), new HashSet<>(list));
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
    public VehicleSetupStatus deleteThings(
            List<SimulationMetaData> simConfigMap, // Map<String, S3Storage.Companion.BucketAndKey>,
            String policyName,
            Boolean deletePolicy,
            Boolean deleteCert
            ) {
        AtomicInteger counter = new AtomicInteger();
        Map<Integer, List<SimulationMetaData>> listOfChunks = simConfigMap.stream()
                .collect(Collectors.groupingBy(it -> counter.getAndIncrement() / CREATE_DELETE_THINGS_BATCH_SIZE));
        Map<String, List<String>> deleteThingResponse = new HashMap<>();
        listOfChunks.forEach((key, value) ->{
            value.forEach(it ->{
                List<String> principalList = deleteThing(it.getVehicleId());
                deleteThingResponse.put(it.getVehicleId(), principalList);
            });
        });
        // To delete certs and keys, we first group by bucket and perform batch deletion per bucket
        Map<String, List<String> > map1 = new HashMap<>();
        Map<String, List<SimulationMetaData>> map= simConfigMap.stream()
                .collect(Collectors.groupingBy(it -> it.getS3().getBucket()));
        map.forEach((key, value) -> {
                    List<String> list1 = new ArrayList<>();
                    value.forEach(i -> {
                        list1.add(i.getVehicleId() + "/"+ CERTIFICATE_FILE_NAME);
                        list1.add(i.getVehicleId() + "/"+ PRIVATE_KEY_FILE_NAME);
                    });
                    map1.put(key, list1);
        });
        map1.forEach(s3Storage::deleteObjects);
        // If raise exception policy not found, we should log it as Error but continue the cleanup
        if (deletePolicy) {
            try {
                deletePolicy(policyName);
            } catch ( ResourceNotFoundException ex) {
                // This raise exception could be thrown if the Policy no longer exist.
                // We should log it as Error but should not re-throw the exception as we cannot delete a non-existed policy
                log.error("Policy {} not found during deletion attempt", policyName);
            }
        }
        if (deleteCert) {
            List<String> certsToDelete = new ArrayList<>();
            deleteThingResponse.forEach((key, value) ->{
                certsToDelete.addAll(value);
            });
            deleteCerts(new HashSet<>(certsToDelete));
        }
        Set<String> deletedThings = deleteThingResponse.keySet();
        List<String> lst = simConfigMap.stream().map(SimulationMetaData::getVehicleId).collect(Collectors.toList());
        lst.removeAll(deletedThings);
        return new VehicleSetupStatus(deletedThings, new HashSet<>(lst));
    }

    /**
     * This function invokes IoTClient to return IoT Core Device Data End Point
     *
     * @return IoT Device Data End Point address
     */
    public String getIoTCoreDataEndPoint(String endpointType) {
        try {
            return iotClient.describeEndpoint(DescribeEndpointRequest.builder().endpointType(endpointType).build())
                    .get().endpointAddress();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public String getIamPolicy( String bucketName) {
        return "{\n\"Version\": \"2012-10-17\",\n\"Statement\": [\n{\n\"Effect\": \"Allow\",\n\"Action\": [\n\"s3:PutObject\",\n\"s3:ListBucket\"\n],\n\"Resource\": [\n\"arn:aws:s3:::" + bucketName + "\",\n\"arn:aws:s3:::" + bucketName + "/*\"\n]\n},\n{\n\"Effect\": \"Allow\",\n\"Action\": [\n\"kms:GenerateDataKey\"\n],\n\"Resource\": [\n\"*\"\n]\n}\n]\n}";
    }
}
