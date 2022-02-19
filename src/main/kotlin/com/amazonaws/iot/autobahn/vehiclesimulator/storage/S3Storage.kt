package com.amazonaws.iot.autobahn.vehiclesimulator.storage

import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import java.security.MessageDigest
import java.util.Base64

class S3Storage(private var client: S3Client) {

    fun put(s3Url: String, data: ByteArray) {
        val bucket = s3Url.substringAfter("S3://").substringBefore("/")
        val key = s3Url.substringAfter("$bucket/")
        client.putObject(
            { builder -> builder.bucket(bucket).key(key).contentMD5(getMD5Digest(data)) },
            RequestBody.fromBytes(data)
        )
    }

    fun put(bucket: String, key: String, data: ByteArray) {
        client.putObject(
            { builder -> builder.bucket(bucket).key(key).contentMD5(getMD5Digest(data)) },
            RequestBody.fromBytes(data)
        )
    }

    fun deleteObjectsFromSameBucket(s3Url: List<String>) {
        if (s3Url.isNotEmpty()) {
            val bucket = s3Url[0].substringAfter("S3://").substringBefore("/")
            val keys = s3Url.map { it.substringAfter("$bucket/") }
            client.deleteObjects { builder ->
                builder.bucket(bucket).delete { it ->
                    it.objects(
                        keys.map { ObjectIdentifier.builder().key(it).build() }
                    )
                }
            }
        }
    }

    companion object {
        private val base64Encoder = Base64.getEncoder()

        private fun getMD5Digest(byteArray: ByteArray): String {
            val md5messageDigest = MessageDigest.getInstance("MD5")
            return base64Encoder.encodeToString(md5messageDigest.digest(byteArray))
        }
    }
}
