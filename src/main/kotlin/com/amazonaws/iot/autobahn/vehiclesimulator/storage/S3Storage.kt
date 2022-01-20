package com.amazonaws.iot.autobahn.vehiclesimulator.storage

import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import java.security.MessageDigest
import java.util.Base64

class S3Storage(private var client: S3Client) {

    fun put(bucket: String, key: String, data: ByteArray) {
        client.putObject(
            { builder -> builder.bucket(bucket).key(key).contentMD5(getMD5Digest(data)) },
            RequestBody.fromBytes(data)
        )
    }

    companion object {
        private val base64Encoder = Base64.getEncoder()

        private fun getMD5Digest(byteArray: ByteArray): String {
            val md5messageDigest = MessageDigest.getInstance("MD5")
            return base64Encoder.encodeToString(md5messageDigest.digest(byteArray))
        }
    }
}
