package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import com.amazonaws.iot.autobahn.vehiclesimulator.storage.S3Storage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class UploadToS3CommandTest {

    private val s3Storage = mockk<S3Storage>()
    private val command = UploadToS3Command(s3Storage)

    @Test
    fun `test valid input is uploaded to the bucket at the specified location`() {
        command.bucket = "some-bucket"
        command.key = "some-key"
        command.data = "some-data"

        every {
            s3Storage.put(any(), any(), any())
        } returns Unit

        val result = command.call()

        Assertions.assertEquals(0, result)
        verify(exactly = 1) { s3Storage.put(command.bucket, command.key, command.data.toByteArray(Charsets.UTF_8)) }
    }
}
