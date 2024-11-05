package com.amazonaws.iot.fleetwise.vehiclesimulator.storage;

import kotlin.jvm.internal.Intrinsics;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
public final class BucketAndKey {

    private final String bucket;

    private final String key;

    /*public BucketAndKey( String bucket,  String key) {
        super();
        this.bucket = bucket;
        this.key = key;
    }


    public final String getBucket() {
        return this.bucket;
    }


    public final String getKey() {
        return this.key;
    }


    public final String component1() {
        return this.bucket;
    }


    public final String component2() {
        return this.key;
    }


    public final BucketAndKey copy( String bucket,  String key) {
        Intrinsics.checkNotNullParameter(bucket, "bucket");
        Intrinsics.checkNotNullParameter(key, "key");
        return new BucketAndKey(bucket, key);
    }


    public String toString() {
        return "BucketAndKey(bucket=" + this.bucket + ", key=" + this.key + ')';
    }

    public int hashCode() {
        int result = this.bucket.hashCode();
        result = result * 31 + this.key.hashCode();
        return result;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof BucketAndKey var2)) {
            return false;
        } else {
            if (!Intrinsics.areEqual(this.bucket, var2.bucket)) {
                return false;
            } else {
                return Intrinsics.areEqual(this.key, var2.key);
            }
        }
    }*/
}
