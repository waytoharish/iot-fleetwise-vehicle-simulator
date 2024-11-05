package com.amazonaws.iot.fleetwise.vehiclesimulator.edgeConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import kotlin.jvm.internal.Intrinsics;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.Nullable;

@Data @Jacksonized
@Builder
public final class Config {

    @JsonProperty("version")
    private String version;

    @JsonProperty("networkInterfaces")
    private Object networkInterfaces;

    @JsonProperty("staticConfig")
    private StaticConfig staticConfig;


/* public Config copy(@JsonProperty("version") String version, @JsonProperty("networkInterfaces") Object networkInterfaces, @JsonProperty("staticConfig") StaticConfig staticConfig) {
        Intrinsics.checkNotNullParameter(version, "version");
        Intrinsics.checkNotNullParameter(networkInterfaces, "networkInterfaces");
        Intrinsics.checkNotNullParameter(staticConfig, "staticConfig");


        return new Config(version, networkInterfaces, staticConfig);
    }*/
}
/* public Config(@JsonProperty("version")  String version, @JsonProperty("networkInterfaces")  Object networkInterfaces, @JsonProperty("staticConfig")  StaticConfig staticConfig) {
        super();
        this.version = version;
        this.networkInterfaces = networkInterfaces;
        this.staticConfig = staticConfig;
    }


    public final String getVersion() {
        return this.version;
    }


    public final Object getNetworkInterfaces() {
        return this.networkInterfaces;
    }


    public final StaticConfig getStaticConfig() {
        return this.staticConfig;
    }


    public final String component1() {
        return this.version;
    }


    public final Object component2() {
        return this.networkInterfaces;
    }


    public final StaticConfig component3() {
        return this.staticConfig;
    }


    public final Config copy(@JsonProperty("version")  String version, @JsonProperty("networkInterfaces")  Object networkInterfaces, @JsonProperty("staticConfig")  StaticConfig staticConfig) {
       Intrinsics.checkNotNullParameter(version, "version");
        Intrinsics.checkNotNullParameter(networkInterfaces, "networkInterfaces");
        Intrinsics.checkNotNullParameter(staticConfig, "staticConfig");


        return new Config(version, networkInterfaces, staticConfig);
    }


    public String toString() {
        return "Config(version=" + this.version + ", networkInterfaces=" + this.networkInterfaces + ", staticConfig=" + this.staticConfig + ')';
    }

    public int hashCode() {
        int result = this.version.hashCode();
        result = result * 31 + this.networkInterfaces.hashCode();
        result = result * 31 + this.staticConfig.hashCode();
        return result;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof Config)) {
            return false;
        } else {
            Config var2 = (Config)other;
            if (!Intrinsics.areEqual(this.version, var2.version)) {
                return false;
            } else if (!Intrinsics.areEqual(this.networkInterfaces, var2.networkInterfaces)) {
                return false;
            } else {
                return Intrinsics.areEqual(this.staticConfig, var2.staticConfig);
            }
        }*/