// StaticConfig.java
package com.amazonaws.iot.fleetwise.vehiclesimulator.edgeConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import kotlin.jvm.internal.Intrinsics;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.Nullable;

@Data @Jacksonized
@Builder
public  class StaticConfig {

    @JsonProperty("bufferSizes")
    private  Object bufferSizes;

    @JsonProperty("threadIdleTimes")
    private  Object threadIdleTimes;

    @JsonProperty("persistency")
    private  Object persistency;

    @JsonProperty("internalParameters")
    private  Object internalParameters;

    @JsonProperty("publishToCloudParameters")
    private  Object publishToCloudParameters;

    @JsonProperty("mqttConnection")
    private  MqttConnectionConfig mqttConnectionConfig;
    @Nullable
    @JsonProperty("credentialsProvider")
    private  CredentialsProvider credentialsProvider;

    @Nullable
    @JsonProperty("s3Upload")
    private  S3Upload s3Upload;

   /* public StaticConfig(@JsonProperty("bufferSizes")  Object bufferSizes, @JsonProperty("threadIdleTimes")  Object threadIdleTimes, @JsonProperty("persistency")  Object persistency, @JsonProperty("internalParameters")  Object internalParameters, @JsonProperty("publishToCloudParameters")  Object publishToCloudParameters, @JsonProperty("mqttConnection")  MqttConnectionConfig mqttConnectionConfig, @JsonProperty("credentialsProvider") @Nullable CredentialsProvider credentialsProvider, @JsonProperty("s3Upload") @Nullable S3Upload s3Upload) {
        super();
        this.bufferSizes = bufferSizes;
        this.threadIdleTimes = threadIdleTimes;
        this.persistency = persistency;
        this.internalParameters = internalParameters;
        this.publishToCloudParameters = publishToCloudParameters;
        this.mqttConnectionConfig = mqttConnectionConfig;
        this.credentialsProvider = credentialsProvider;
        this.s3Upload = s3Upload;
    }


    public final Object getBufferSizes() {
        return this.bufferSizes;
    }


    public final Object getThreadIdleTimes() {
        return this.threadIdleTimes;
    }


    public final Object getPersistency() {
        return this.persistency;
    }


    public final Object getInternalParameters() {
        return this.internalParameters;
    }


    public final Object getPublishToCloudParameters() {
        return this.publishToCloudParameters;
    }


    public final MqttConnectionConfig getMqttConnectionConfig() {
        return this.mqttConnectionConfig;
    }

    @Nullable
    public final CredentialsProvider getCredentialsProvider() {
        return this.credentialsProvider;
    }

    @Nullable
    public final S3Upload getS3Upload() {
        return this.s3Upload;
    }


    public final Object component1() {
        return this.bufferSizes;
    }


    public final Object component2() {
        return this.threadIdleTimes;
    }


    public final Object component3() {
        return this.persistency;
    }


    public final Object component4() {
        return this.internalParameters;
    }


    public final Object component5() {
        return this.publishToCloudParameters;
    }


    public final MqttConnectionConfig component6() {
        return this.mqttConnectionConfig;
    }

    @Nullable
    public final CredentialsProvider component7() {
        return this.credentialsProvider;
    }

    @Nullable
    public final S3Upload component8() {
        return this.s3Upload;
    }





    public String toString() {
        return "StaticConfig(bufferSizes=" + this.bufferSizes + ", threadIdleTimes=" + this.threadIdleTimes + ", persistency=" + this.persistency + ", internalParameters=" + this.internalParameters + ", publishToCloudParameters=" + this.publishToCloudParameters + ", mqttConnectionConfig=" + this.mqttConnectionConfig + ", credentialsProvider=" + this.credentialsProvider + ", s3Upload=" + this.s3Upload + ')';
    }

    public int hashCode() {
        int result = this.bufferSizes.hashCode();
        result = result * 31 + this.threadIdleTimes.hashCode();
        result = result * 31 + this.persistency.hashCode();
        result = result * 31 + this.internalParameters.hashCode();
        result = result * 31 + this.publishToCloudParameters.hashCode();
        result = result * 31 + this.mqttConnectionConfig.hashCode();
        result = result * 31 + (this.credentialsProvider == null ? 0 : this.credentialsProvider.hashCode());
        result = result * 31 + (this.s3Upload == null ? 0 : this.s3Upload.hashCode());
        return result;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof StaticConfig)) {
            return false;
        } else {
            StaticConfig var2 = (StaticConfig)other;
            if (!Intrinsics.areEqual(this.bufferSizes, var2.bufferSizes)) {
                return false;
            } else if (!Intrinsics.areEqual(this.threadIdleTimes, var2.threadIdleTimes)) {
                return false;
            } else if (!Intrinsics.areEqual(this.persistency, var2.persistency)) {
                return false;
            } else if (!Intrinsics.areEqual(this.internalParameters, var2.internalParameters)) {
                return false;
            } else if (!Intrinsics.areEqual(this.publishToCloudParameters, var2.publishToCloudParameters)) {
                return false;
            } else if (!Intrinsics.areEqual(this.mqttConnectionConfig, var2.mqttConnectionConfig)) {
                return false;
            } else if (!Intrinsics.areEqual(this.credentialsProvider, var2.credentialsProvider)) {
                return false;
            } else {
                return Intrinsics.areEqual(this.s3Upload, var2.s3Upload);
            }
        }
    }*/

   public StaticConfig copy(@JsonProperty("bufferSizes")  Object bufferSizes,
                            @JsonProperty("threadIdleTimes")  Object threadIdleTimes,
                            @JsonProperty("persistency")  Object persistency,
                            @JsonProperty("internalParameters")  Object internalParameters,
                            @JsonProperty("publishToCloudParameters")  Object publishToCloudParameters,
                            @JsonProperty("mqttConnection")  MqttConnectionConfig mqttConnectionConfig,
                            @JsonProperty("credentialsProvider") @Nullable CredentialsProvider credentialsProvider,
                            @JsonProperty("s3Upload") @Nullable S3Upload s3Upload) {
        return new StaticConfig(bufferSizes, threadIdleTimes, persistency, internalParameters, publishToCloudParameters, mqttConnectionConfig, credentialsProvider, s3Upload);
    }
}