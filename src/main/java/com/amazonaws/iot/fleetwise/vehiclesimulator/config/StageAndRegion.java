package com.amazonaws.iot.fleetwise.vehiclesimulator.config;

import kotlin.jvm.internal.Intrinsics;
import lombok.Data;

public record StageAndRegion(String stage, String region) {

    public final boolean isAlpha() {
        return Intrinsics.areEqual(this.stage, "alpha");
    }

    public final boolean isProd() {
        return Intrinsics.areEqual(this.stage, "prod");
    }

    /*public StageAndRegion( String stage,  String region) {
        this.stage = stage;
        this.region = region;
    }


    public final String getStage() {
        return this.stage;
    }


    public final String getRegion() {
        return this.region;
    }

    public final boolean isAlpha() {
        return Intrinsics.areEqual(this.stage, "alpha");
    }

    public final boolean isProd() {
        return Intrinsics.areEqual(this.stage, "prod");
    }


    public final String component1() {
        return this.stage;
    }


    public final String component2() {
        return this.region;
    }*/


  /*  public final StageAndRegion copy( String stage,  String region) {
        Intrinsics.checkNotNullParameter(stage, "stage");
        Intrinsics.checkNotNullParameter(region, "region");
        return new StageAndRegion(stage, region);
    }*/


    /*public String toString() {
        return "StageAndRegion(stage=" + this.stage + ", region=" + this.region + ')';
    }

    public int hashCode() {
        int result = this.stage.hashCode();
        result = result * 31 + this.region.hashCode();
        return result;
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof StageAndRegion)) {
            return false;
        } else {
            StageAndRegion var2 = (StageAndRegion)other;
            if (!Intrinsics.areEqual(this.stage, var2.stage)) {
                return false;
            } else {
                return Intrinsics.areEqual(this.region, var2.region);
            }
        }
    }*/
}
