
package com.amazonaws.iot.fleetwise.vehiclesimulator.storage;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data @Jacksonized @Builder
public  class S3 {
    @JsonProperty("bucket")
    private  String bucket;

    @JsonProperty("key")
    private  String key;

   /* public S3(@JsonProperty("bucket")  String bucket, @JsonProperty("key")  String key) {
        super();
        this.bucket = bucket;
        this.key = key;
    }*/


    /*public final String getBucket() {
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
    }*/


   /* public final S3 copy(@JsonProperty("bucket")  String bucket, @JsonProperty("key")  String key) {
        return new S3(bucket, key);
    }*/


   /* public String toString() {
        return "S3(bucket=" + this.bucket + ", key=" + this.key + ')';
    }

    public int hashCode() {
        int result = this.bucket.hashCode();
        result = result * 31 + this.key.hashCode();
        return result;
    }*/

   /* public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof S3 var2)) {
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
