package com.amazonaws.iot.autobahn.vehiclesimulator.storage

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.security.MessageDigest
import java.util.Base64
import java.util.function.Consumer

internal class S3StorageTest {

    private val client = mockk<S3Client>()
    private val s3Storage = S3Storage(client)

    @Test
    fun put() {
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
}
