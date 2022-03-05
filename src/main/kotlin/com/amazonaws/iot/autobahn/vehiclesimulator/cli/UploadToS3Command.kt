package com.amazonaws.iot.autobahn.vehiclesimulator.cli

import com.amazonaws.iot.autobahn.vehiclesimulator.storage.S3Storage
import picocli.CommandLine
import picocli.CommandLine.Command
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import java.util.concurrent.Callable

@Command(
    name = "upload",
    description = ["Uploads something to S3"],
)
class UploadToS3Command(private val s3Storage: S3Storage) : Callable<Int> {

    constructor() : this(
        S3Storage(S3AsyncClient.builder().region(Region.US_WEST_2).build())
    )

    @CommandLine.Option(required = true, names = ["--bucket", "-b"])
    lateinit var bucket: String

    @CommandLine.Option(required = true, names = ["--key", "-k"])
    lateinit var key: String

    @CommandLine.Option(required = true, names = ["--data", "-d"])
    lateinit var data: String

    override fun call(): Int {
        s3Storage.put(bucket, key, data.toByteArray(Charsets.UTF_8))
        println("Successfully uploaded!")
        return 0
    }
}
