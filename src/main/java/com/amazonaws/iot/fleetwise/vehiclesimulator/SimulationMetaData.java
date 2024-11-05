package com.amazonaws.iot.fleetwise.vehiclesimulator;

import com.amazonaws.iot.fleetwise.vehiclesimulator.storage.S3;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import kotlin.jvm.internal.Intrinsics;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.Nullable;

@JsonIgnoreProperties(
        ignoreUnknown = true
)
@Data
@Jacksonized
@Builder
public  class SimulationMetaData {

    @JsonProperty("vehicleID")
    private  String vehicleId;

    @JsonProperty("s3")
    private  S3 s3;

    @JsonProperty("provisionThing")
    private boolean provisionThing;

    @Nullable
    @JsonProperty("deviceCertificateConfig")
    private DeviceCertificateConfig deviceCertificateConfig;

    /*public SimulationMetaData(@JsonProperty("vehicleID")  String vehicleId,
                              @JsonProperty("s3")  S3 s3,
                              @JsonProperty("provisionThing") boolean provisionThing,
                              @JsonProperty("deviceCertificateConfig") @Nullable DeviceCertificateConfig deviceCertificateConfig) {
        super();
        this.vehicleId = vehicleId;
        this.s3 = s3;
        this.provisionThing = provisionThing;
        this.deviceCertificateConfig = deviceCertificateConfig;
    }


    public final String getVehicleId() {
        return this.vehicleId;
    }


    public final S3 getS3() {
        return this.s3;
    }

    public final boolean getProvisionThing() {
        return this.provisionThing;
    }

    public final void setProvisionThing(boolean var1) {
        this.provisionThing = var1;
    }

    @Nullable
    public final DeviceCertificateConfig getDeviceCertificateConfig() {
        return this.deviceCertificateConfig;
    }

    public final void setDeviceCertificateConfig(@Nullable DeviceCertificateConfig var1) {
        this.deviceCertificateConfig = var1;
    }


    public final String component1() {
        return this.vehicleId;
    }


    public final S3 component2() {
        return this.s3;
    }

    public final boolean component3() {
        return this.provisionThing;
    }

    @Nullable
    public final DeviceCertificateConfig component4() {
        return this.deviceCertificateConfig;
    }
*/

   /* public final SimulationMetaData copy(@JsonProperty("vehicleID")  String vehicleId, @JsonProperty("s3")  S3 s3, @JsonProperty("provisionThing") boolean provisionThing, @JsonProperty("deviceCertificateConfig") @Nullable DeviceCertificateConfig deviceCertificateConfig) {
        Intrinsics.checkNotNullParameter(vehicleId, "vehicleId");
        Intrinsics.checkNotNullParameter(s3, "s3");
        return new SimulationMetaData(vehicleId, s3, provisionThing, deviceCertificateConfig);
    }*/


  /*  public String toString() {
        return "SimulationMetaData(vehicleId=" + this.vehicleId + ", s3=" + this.s3 + ", provisionThing=" + this.provisionThing + ", deviceCertificateConfig=" + this.deviceCertificateConfig + ')';
    }
*/
    /*public int hashCode() {
        int result = this.vehicleId.hashCode();
        result = result * 31 + this.s3.hashCode();
        result = result * 31 + Boolean.hashCode(this.provisionThing);
        result = result * 31 + (this.deviceCertificateConfig == null ? 0 : this.deviceCertificateConfig.hashCode());
        return result;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof SimulationMetaData)) {
            return false;
        } else {
            SimulationMetaData var2 = (SimulationMetaData)other;
            if (!Intrinsics.areEqual(this.vehicleId, var2.vehicleId)) {
                return false;
            } else if (!Intrinsics.areEqual(this.s3, var2.s3)) {
                return false;
            } else if (this.provisionThing != var2.provisionThing) {
                return false;
            } else {
                return Intrinsics.areEqual(this.deviceCertificateConfig, var2.deviceCertificateConfig);
            }
        }
    }*/
}