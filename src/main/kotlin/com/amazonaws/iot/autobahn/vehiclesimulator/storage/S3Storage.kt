package com.amazonaws.iot.autobahn.vehiclesimulator.storage

import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import java.security.MessageDigest
import java.util.Base64

class S3Storage(private var client: S3Client) {
    fun put(bucket: String, key: String, data: ByteArray) {
        client.putObject(
            { builder -> builder.bucket(bucket).key(key).contentMD5(getMD5Digest(data)) },
            RequestBody.fromBytes(data)
        )
    }

    fun deleteObjects(bucket: String, keyList: List<String>) {
        // As maximum number of objects per IoTClient deleteObjects API can handle is 1000. Need to chunk the list
        // if list length is larger than 1000
        keyList.chunked(MAX_NUM_OF_OBJECTS_PER_DELETION)
            .forEach { chunkedKeyList ->
                client.deleteObjects { builder ->
                    builder.bucket(bucket).delete { it ->
                        it.objects(
                            chunkedKeyList.map { ObjectIdentifier.builder().key(it).build() }
                        )
                    }
                }
            }
    }

    companion object {
        data class BucketAndKey(
            val bucket: String,
            val key: String
        )

        private val base64Encoder = Base64.getEncoder()

        private fun getMD5Digest(byteArray: ByteArray): String {
            val md5messageDigest = MessageDigest.getInstance("MD5")
            return base64Encoder.encodeToString(md5messageDigest.digest(byteArray))
        }

        // Maximum number of objects per deleteObjects API can handle is 1000
        private const val MAX_NUM_OF_OBJECTS_PER_DELETION = 1000
    }
}
