package com.amazonaws.iot.fleetwise.vehiclesimulator;

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


@Data @Jacksonized @Builder
public  class DeviceCertificateConfig {

    @JsonProperty("pcaArn")
    private  String pcaArn;

    @JsonProperty("commonName")
    private  String commonName;

    @JsonProperty("organization")
    private  String organization;

    @JsonProperty("countryCode")
    private  String countryCode;

    @JsonProperty("validityDays")
    private  long validityDays;

    /*public DeviceCertificateConfig(@JsonProperty("pcaArn")  String pcaArn, @JsonProperty("commonName")  String commonName, @JsonProperty("organization")  String organization, @JsonProperty("countryCode")  String countryCode, @JsonProperty("validityDays") long validityDays) {
        this.pcaArn = pcaArn;
        this.commonName = commonName;
        this.organization = organization;
        this.countryCode = countryCode;
        this.validityDays = validityDays;
    }


    public final String getPcaArn() {
        return this.pcaArn;
    }


    public final String getCommonName() {
        return this.commonName;
    }


    public final String getOrganization() {
        return this.organization;
    }


    public final String getCountryCode() {
        return this.countryCode;
    }

    public final long getValidityDays() {
        return this.validityDays;
    }


    public final String component1() {
        return this.pcaArn;
    }


    public final String component2() {
        return this.commonName;
    }


    public final String component3() {
        return this.organization;
    }


    public final String component4() {
        return this.countryCode;
    }

    public final long component5() {
        return this.validityDays;
    }


    public final DeviceCertificateConfig copy(@JsonProperty("pcaArn")  String pcaArn, @JsonProperty("commonName")  String commonName, @JsonProperty("organization")  String organization, @JsonProperty("countryCode")  String countryCode, @JsonProperty("validityDays") long validityDays) {
        Intrinsics.checkNotNullParameter(pcaArn, "pcaArn");
        Intrinsics.checkNotNullParameter(commonName, "commonName");
        Intrinsics.checkNotNullParameter(organization, "organization");
        Intrinsics.checkNotNullParameter(countryCode, "countryCode");
        return new DeviceCertificateConfig(pcaArn, commonName, organization, countryCode, validityDays);
    }


    public String toString() {
        return "DeviceCertificateConfig(pcaArn=" + this.pcaArn + ", commonName=" + this.commonName + ", organization=" + this.organization + ", countryCode=" + this.countryCode + ", validityDays=" + this.validityDays + ')';
    }

    public int hashCode() {
        int result = this.pcaArn.hashCode();
        result = result * 31 + this.commonName.hashCode();
        result = result * 31 + this.organization.hashCode();
        result = result * 31 + this.countryCode.hashCode();
        result = result * 31 + Long.hashCode(this.validityDays);
        return result;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof DeviceCertificateConfig var2)) {
            return false;
        } else {
            if (!Intrinsics.areEqual(this.pcaArn, var2.pcaArn)) {
                return false;
            } else if (!Intrinsics.areEqual(this.commonName, var2.commonName)) {
                return false;
            } else if (!Intrinsics.areEqual(this.organization, var2.organization)) {
                return false;
            } else if (!Intrinsics.areEqual(this.countryCode, var2.countryCode)) {
                return false;
            } else {
                return this.validityDays == var2.validityDays;
            }
        }
    }*/
}