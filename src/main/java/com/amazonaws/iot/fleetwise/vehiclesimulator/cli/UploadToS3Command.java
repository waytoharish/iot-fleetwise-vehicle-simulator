package com.amazonaws.iot.fleetwise.vehiclesimulator.cli;

import com.amazonaws.iot.fleetwise.vehiclesimulator.storage.S3Storage;
import java.util.concurrent.Callable;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@Component
@Command(
        name = "upload",
        description = {"Uploads something to S3"}
)
@Data
public final class UploadToS3Command implements Callable<Integer> {
    @Option(
            names = {"--bucket", "-b"},
            required = true
    )
    public String bucket;
    @Option(
            names = {"--key", "-k"},
            required = true
    )
    public String key;
    @Option(
            names = {"--data", "-d"},
            required = true
    )
    public String data;
    @Option(
            names = {"--region", "-r"},
            required = true
    )
  public String region;

    private final Logger log = LoggerFactory.getLogger(UploadToS3Command.class);


      /*public final String getBucket() {
        return this.bucket;
    }

    public final void setBucket( String var1) {
        this.bucket = bucket;
    }


    public final String getKey() {
        return this.key;
    }

    public final void setKey( String key) {
        this.key = key;
    }


    public final String getData() {
        return this.data;
    }

    public final void setData( String data) {
        this.data = data;
    }


    public final String getRegion() {
        return this.region;
    }

    public final void setRegion( String region) {
        this.region = region;
    }*/


    public Integer call() {
        S3Storage s3Storage = new S3Storage(S3AsyncClient.builder().region(Region.of(region)).build());
        s3Storage.put(bucket, key, data.getBytes());
        log.info("Successfully uploaded!");
        return 0;
    }

}
