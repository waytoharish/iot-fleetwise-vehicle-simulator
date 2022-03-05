package com.amazonaws.iot.autobahn.vehiclesimulator.storage

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

internal class S3StorageTest {
    private val client = mockk<S3AsyncClient>()
    private val s3Storage = S3Storage(client)

    @Test
    fun `when put called with bucket, key and data`() {
        val bucket = "some-bucket"
        val key = "some-key"
        val data = "some-data"

        val putObjectRequestList = mutableListOf<Consumer<PutObjectRequest.Builder>>()
        every {
            client.putObject(capture(putObjectRequestList), any<AsyncRequestBody>())
        } returns CompletableFuture.completedFuture(PutObjectResponse.builder().build())

        runBlocking { s3Storage.put(bucket, key, data.toByteArray(Charsets.UTF_8)) }
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
        val keys = listOf("file1", "file2", "file3")

        val deleteObjectsRequest = mutableListOf<Consumer<DeleteObjectsRequest.Builder>>()
        every {
            client.deleteObjects(capture(deleteObjectsRequest))
        } returns CompletableFuture.completedFuture(DeleteObjectsResponse.builder().build())

        runBlocking { s3Storage.deleteObjects(bucket, keys) }

        Assertions.assertEquals(
            bucket,
            deleteObjectsRequest.map {
                val builder = DeleteObjectsRequest.builder()
                it.accept(builder)
                builder.build().bucket()
            }[0]
        )

        Assertions.assertEquals(
            keys.map { ObjectIdentifier.builder().key(it).build() },
            deleteObjectsRequest.flatMap {
                val builder = DeleteObjectsRequest.builder()
                it.accept(builder)
                builder.build().delete().objects()
            }
        )
    }
}
