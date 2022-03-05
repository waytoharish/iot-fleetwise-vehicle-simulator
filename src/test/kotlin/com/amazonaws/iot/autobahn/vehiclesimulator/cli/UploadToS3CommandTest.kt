package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import com.amazonaws.iot.autobahn.vehiclesimulator.storage.S3Storage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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

        coEvery {
            s3Storage.put(any(), any(), any())
        } returns Unit

        val result = command.call()

        Assertions.assertEquals(0, result)
        coVerify(exactly = 1) { s3Storage.put(command.bucket, command.key, command.data.toByteArray(Charsets.UTF_8)) }
    }
}
