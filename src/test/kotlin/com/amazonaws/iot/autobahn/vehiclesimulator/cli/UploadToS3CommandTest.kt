package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import com.amazonaws.iot.autobahn.vehiclesimulator.storage.S3Storage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class UploadToS3CommandTest {
    private val command = UploadToS3Command()

    @Test
    fun `test valid input is uploaded to the bucket at the specified location`() {
        command.bucket = "some-bucket"
        command.key = "some-key"
        command.data = "some-data"
        command.region = "some-region"
        mockkConstructor(S3Storage::class)
        coEvery {
            anyConstructed<S3Storage>().put(any(), any(), any())
        } returns Unit

        val result = command.call()

        Assertions.assertEquals(0, result)
        coVerify(exactly = 1) { anyConstructed<S3Storage>().put(command.bucket, command.key, command.data.toByteArray(Charsets.UTF_8)) }
        unmockkConstructor(S3Storage::class)
    }
}
