package com.amazonaws.iot.fleetwise.vehiclesimulator.storage;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.Nullable;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;


public final class S3Storage {

    private final S3AsyncClient client;

    private final Base64.Encoder base64Encoder = Base64.getEncoder();
    private static final int MAX_NUM_OF_OBJECTS_PER_DELETION = 1000;

    public S3Storage(S3AsyncClient client) {
        this.client = client;
    }

    public void put(String bucket, String key, byte[] data) {
        try {
            client.putObject(
                    PutObjectRequest.builder().bucket(bucket.trim().toLowerCase()).key(key).contentMD5(getMD5Digest(data)).build(),
                    AsyncRequestBody.fromBytes(data)
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteObjects( String bucket,  List<String> keyList) {
        AtomicInteger counter = new AtomicInteger();
        Map<Integer, List<String>> listOfChunks = keyList.stream()
                .collect(Collectors.groupingBy(it -> counter.getAndIncrement() / MAX_NUM_OF_OBJECTS_PER_DELETION));

        listOfChunks.forEach((key, value) -> client.deleteObjects(DeleteObjectsRequest.builder().bucket(bucket).delete(item -> {
            item.objects(value.stream().map(m -> ObjectIdentifier.builder().key(m).build()).collect(Collectors.toList()));
        }).build()));
}

    public List<String> listObjects( String bucket,  String key) {
        CompletableFuture<ListObjectsV2Response> listObjectsV2Response= this.client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).prefix(key).build());
        ListObjectsV2Response response = null;
        try {
            response = listObjectsV2Response.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        return response.contents().stream().map(S3Object::key).collect(Collectors.toList());
    }
    private String getMD5Digest(byte[] byteArray) {
        MessageDigest md5messageDigest = null;
        try {
            md5messageDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        String var3 = this.base64Encoder.encodeToString(md5messageDigest.digest(byteArray));
        Intrinsics.checkNotNullExpressionValue(var3, "encodeToString(...)");
        return var3;
    }



}