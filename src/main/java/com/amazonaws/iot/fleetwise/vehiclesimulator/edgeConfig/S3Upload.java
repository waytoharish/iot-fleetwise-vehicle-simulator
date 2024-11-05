// S3Upload.java
package com.amazonaws.iot.fleetwise.vehiclesimulator.edgeConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.Nullable;

@Data @Jacksonized
@Builder
public final class S3Upload {
    @JsonProperty("maxEnvelopeSize")
    private final long maxEnvelopeSize;

    @JsonProperty("multipartSize")
    private final long multipartSize;

    @JsonProperty("maxConnections")
    private final long maxConnections;

    /*public S3Upload(@JsonProperty("maxEnvelopeSize") long maxEnvelopeSize, @JsonProperty("multipartSize") long multipartSize, @JsonProperty("maxConnections") long maxConnections) {
        this.maxEnvelopeSize = maxEnvelopeSize;
        this.multipartSize = multipartSize;
        this.maxConnections = maxConnections;
    }

    public final long getMaxEnvelopeSize() {
        return this.maxEnvelopeSize;
    }

    public final long getMultipartSize() {
        return this.multipartSize;
    }

    public final long getMaxConnections() {
        return this.maxConnections;
    }

    public final long component1() {
        return this.maxEnvelopeSize;
    }

    public final long component2() {
        return this.multipartSize;
    }

    public final long component3() {
        return this.maxConnections;
    }*/

  /*  public final S3Upload copy(@JsonProperty("maxEnvelopeSize") long maxEnvelopeSize, @JsonProperty("multipartSize") long multipartSize, @JsonProperty("maxConnections") long maxConnections) {
        return new S3Upload(maxEnvelopeSize, multipartSize, maxConnections);
    }
*/
  /*  public String toString() {
        return "S3Upload(maxEnvelopeSize=" + this.maxEnvelopeSize + ", multipartSize=" + this.multipartSize + ", maxConnections=" + this.maxConnections + ')';
    }*/

   /* public int hashCode() {
        int result = Long.hashCode(this.maxEnvelopeSize);
        result = result * 31 + Long.hashCode(this.multipartSize);
        result = result * 31 + Long.hashCode(this.maxConnections);
        return result;
    }*/

    /*public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof S3Upload)) {
            return false;
        } else {
            S3Upload var2 = (S3Upload)other;
            if (this.maxEnvelopeSize != var2.maxEnvelopeSize) {
                return false;
            } else if (this.multipartSize != var2.multipartSize) {
                return false;
            } else {
                return this.maxConnections == var2.maxConnections;
            }
        }
    }*/
}