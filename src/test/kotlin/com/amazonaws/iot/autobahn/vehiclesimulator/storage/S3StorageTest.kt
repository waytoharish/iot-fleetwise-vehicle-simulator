package com.amazonaws.iot.autobahn.vehiclesimulator.storage

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.security.MessageDigest
import java.util.Base64
import java.util.function.Consumer

internal class S3StorageTest {

    private val client = mockk<S3Client>()
    private val s3Storage = S3Storage(client)

    @Test
    fun `when put called with bucket, key and data`() {
        val bucket = "some-bucket"
        val key = "some-key"
        val data = "some-data"

        val putObjectRequestList = mutableListOf<Consumer<PutObjectRequest.Builder>>()
        every {
            client.putObject(capture(putObjectRequestList), any<RequestBody>())
        } returns PutObjectResponse.builder().build()

        s3Storage.put(bucket, key, data.toByteArray(Charsets.UTF_8))
        val actualKey = putObjectRequestList.map {
            val builder = PutObjectRequest.builder()
            it.accept(builder)
            builder.build().key()
        }[0]

        val actualBucket = putObjectRequestList.map {
            val builder = PutObjectRequest.builder()
            it.accept(builder)
            builder.build().bucket()
        }[0]

        val content = putObjectRequestList.map {
            val builder = PutObjectRequest.builder()
            it.accept(builder)
            builder.build().contentMD5()
        }[0]

        Assertions.assertEquals(key, actualKey)
        Assertions.assertEquals(bucket, actualBucket)
        Assertions.assertEquals(Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(data.toByteArray(Charsets.UTF_8))), content)
    }

    @Test
    fun `when put called with s3url and data`() {
        val bucket = "some-bucket"
        val key = "some-key"
        val url = "S3://$bucket/$key"
        val data = "some-data"

        val putObjectRequestList = mutableListOf<Consumer<PutObjectRequest.Builder>>()
        every {
            client.putObject(capture(putObjectRequestList), any<RequestBody>())
        } returns PutObjectResponse.builder().build()

        s3Storage.put(url, data.toByteArray(Charsets.UTF_8))
        val actualKey = putObjectRequestList.map {
            val builder = PutObjectRequest.builder()
            it.accept(builder)
            builder.build().key()
        }[0]

        val actualBucket = putObjectRequestList.map {
            val builder = PutObjectRequest.builder()
            it.accept(builder)
            builder.build().bucket()
        }[0]

        val content = putObjectRequestList.map {
            val builder = PutObjectRequest.builder()
            it.accept(builder)
            builder.build().contentMD5()
        }[0]

        Assertions.assertEquals(key, actualKey)
        Assertions.assertEquals(bucket, actualBucket)
        Assertions.assertEquals(Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(data.toByteArray(Charsets.UTF_8))), content)
    }

    @Test
    fun `when deleteObjectsFromSameBucket called with a list of S3 url that belong to the same S3 bucket`() {
        val bucket = "some-bucket"
        val s3Urls = listOf("file1", "file2", "file3").map { "S3://$bucket/$it" }

        val deleteObjectsRequest = mutableListOf<Consumer<DeleteObjectsRequest.Builder>>()
        every {
            client.deleteObjects(capture(deleteObjectsRequest))
        } returns DeleteObjectsResponse.builder().build()

        s3Storage.deleteObjectsFromSameBucket(s3Urls)

        Assertions.assertEquals(
            bucket,
            deleteObjectsRequest.map {
                val builder = DeleteObjectsRequest.builder()
                it.accept(builder)
                builder.build().bucket()
            }[0]
        )

        Assertions.assertEquals(
            listOf("file1", "file2", "file3").map { ObjectIdentifier.builder().key(it).build() },
            deleteObjectsRequest.flatMap {
                val builder = DeleteObjectsRequest.builder()
                it.accept(builder)
                builder.build().delete().objects()
            }
        )
    }
}
