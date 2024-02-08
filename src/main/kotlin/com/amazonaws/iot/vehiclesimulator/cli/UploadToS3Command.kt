package com.amazonaws.iot.fleetwise.vehiclesimulator.cli

import com.amazonaws.iot.fleetwise.vehiclesimulator.storage.S3Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Command
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import java.util.concurrent.Callable

@Command(
    name = "upload",
    description = ["Uploads something to S3"],
)
class UploadToS3Command() : Callable<Int> {
    @CommandLine.Option(required = true, names = ["--bucket", "-b"])
    lateinit var bucket: String

    @CommandLine.Option(required = true, names = ["--key", "-k"])
    lateinit var key: String

    @CommandLine.Option(required = true, names = ["--data", "-d"])
    lateinit var data: String

    @CommandLine.Option(required = true, names = ["--region", "-r"])
    lateinit var region: String

    private val log: Logger = LoggerFactory.getLogger(UploadToS3Command::class.java)

    override fun call(): Int {
        val s3Storage = S3Storage(S3AsyncClient.builder().region(Region.of(region)).build())
        runBlocking(Dispatchers.IO) { s3Storage.put(bucket, key, data.toByteArray(Charsets.UTF_8)) }
        log.info("Successfully uploaded!")
        return 0
    }
}
