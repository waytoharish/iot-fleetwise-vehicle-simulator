package com.amazonaws.iot.autobahn.config

class ControlPlaneResources(region: String, disambiguator: String? = null) {
    private val region = region.lowercase()
    private val stage = "prod"
    private val stageAndRegion = StageAndRegion(this.stage, this.region)

    private val disambiguator = if (disambiguator.isNullOrEmpty()) {
        ""
    } else {
        "-$disambiguator"
    }

    val serviceLinkedRoleName = "AWSServiceRoleForIoTFleetWise"

    val stageRegion = "${this.stage}${this.region}"

    private fun topicPrefix(vehicleId: String): String {
        if (this.stageAndRegion.isProd) {
            return "\$aws/iotfleetwise/vehicles/$vehicleId"
        }

        return "\$aws/iotfleetwise/${this.stage}-${this.region}/vehicles/$vehicleId"
    }

    fun getCheckinTopic(vehicleId: String): String = "${topicPrefix(vehicleId)}/checkins"

    fun getSignalsTopic(vehicleId: String): String = "${topicPrefix(vehicleId)}/signals"

     fun getPolicyTopic(vehicleId: String): String = "${topicPrefix(vehicleId)}/collection_schemes"

    fun getDecoderManifestTopic(vehicleId: String): String = "${topicPrefix(vehicleId)}/decoder_manifests"

    data class StageAndRegion(val stage: String, val region: String) {
        val isAlpha: Boolean
            get() = stage == "alpha"
        val isProd: Boolean
            get() = stage == "prod"
    }

}