package com.amazonaws.iot.fleetwise.vehiclesimulator.edgeConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import kotlin.jvm.internal.Intrinsics;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.Nullable;

@Data @Jacksonized
@Builder
public  class CredentialsProvider {

    @JsonProperty("endpointUrl")
    private  String endpointUrl;

    @JsonProperty("roleAlias")
    private  String roleAlias;

   /* public CredentialsProvider(@JsonProperty("endpointUrl")  String endpointUrl, @JsonProperty("roleAlias")  String roleAlias) {
        super();
        this.endpointUrl = endpointUrl;
        this.roleAlias = roleAlias;
    }


    public final String getEndpointUrl() {
        return this.endpointUrl;
    }


    public final String getRoleAlias() {
        return this.roleAlias;
    }


    public final String component1() {
        return this.endpointUrl;
    }


    public final String component2() {
        return this.roleAlias;
    }


    public final CredentialsProvider copy(@JsonProperty("endpointUrl")  String endpointUrl, @JsonProperty("roleAlias")  String roleAlias) {
        Intrinsics.checkNotNullParameter(endpointUrl, "endpointUrl");
        Intrinsics.checkNotNullParameter(roleAlias, "roleAlias");
        return new CredentialsProvider(endpointUrl, roleAlias);
    }


    public String toString() {
        return "CredentialsProvider(endpointUrl=" + this.endpointUrl + ", roleAlias=" + this.roleAlias + ')';
    }

    public int hashCode() {
        int result = this.endpointUrl.hashCode();
        result = result * 31 + this.roleAlias.hashCode();
        return result;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof CredentialsProvider)) {
            return false;
        } else {
            CredentialsProvider var2 = (CredentialsProvider)other;
            if (!Intrinsics.areEqual(this.endpointUrl, var2.endpointUrl)) {
                return false;
            } else {
                return Intrinsics.areEqual(this.roleAlias, var2.roleAlias);
            }
        }
    }*/
}