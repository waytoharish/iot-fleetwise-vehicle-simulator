package com.amazonaws.iot.fleetwise.vehiclesimulator.config;

import kotlin.jvm.internal.Intrinsics;
import lombok.Data;


public record ControlPlaneResources(String region, String stage, StageAndRegion stageAndRegion, String disambiguator,
                                    String serviceLinkedRoleName, String stageRegion) {

    /*public ControlPlaneResources(String region) {
        super();
        this.region = region.toLowerCase(Locale.ROOT);
        this.stage = "prod";
        this.stageAndRegion = new StageAndRegion(this.stage, this.region);
        this.disambiguator = "";
        this.serviceLinkedRoleName = "AWSServiceRoleForIoTFleetWise";
        this.stageRegion = this.stage + this.region;
    }

    public ControlPlaneResources( String region, @Nullable String disambiguator) {
        super();
        this.region = region.toLowerCase(Locale.ROOT);
        this.stage = "prod";
        this.stageAndRegion = new StageAndRegion(this.stage, this.region);
        this.disambiguator = disambiguator == null || ((CharSequence) disambiguator).isEmpty() ? "" : '-' + disambiguator;
        this.serviceLinkedRoleName = "AWSServiceRoleForIoTFleetWise";
        this.stageRegion = this.stage + this.region;
    }


    public final String getServiceLinkedRoleName() {
        return this.serviceLinkedRoleName;
    }


    public final String getStageRegion() {
        return this.stageRegion;
    }*/

    private final String topicPrefix(String vehicleId) {
        return this.stageAndRegion.isProd() ? "$aws/iotfleetwise/vehicles/" + vehicleId : "$aws/iotfleetwise/" + this.stage + '-' + this.region + "/vehicles/" + vehicleId;
    }


    public final String getCheckinTopic(String vehicleId) {
        Intrinsics.checkNotNullParameter(vehicleId, "vehicleId");
        return this.topicPrefix(vehicleId) + "/checkins";
    }


    public final String getSignalsTopic(String vehicleId) {
        Intrinsics.checkNotNullParameter(vehicleId, "vehicleId");
        return this.topicPrefix(vehicleId) + "/signals";
    }


    public final String getPolicyTopic(String vehicleId) {
        Intrinsics.checkNotNullParameter(vehicleId, "vehicleId");
        return this.topicPrefix(vehicleId) + "/collection_schemes";
    }


    public final String getDecoderManifestTopic(String vehicleId) {
        Intrinsics.checkNotNullParameter(vehicleId, "vehicleId");
        return this.topicPrefix(vehicleId) + "/decoder_manifests";
    }


}
