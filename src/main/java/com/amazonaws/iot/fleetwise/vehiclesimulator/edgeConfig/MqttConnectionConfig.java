// MqttConnectionConfig.java
package com.amazonaws.iot.fleetwise.vehiclesimulator.edgeConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import kotlin.jvm.internal.Intrinsics;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.Nullable;

@Data @Jacksonized
@Builder
public class MqttConnectionConfig {

    @JsonProperty("connectionType")
    private  String connectionType;

    @JsonProperty("endpointUrl")
    private  String endPointUrl;

    @JsonProperty("clientId")
    private  String clientId;

    @JsonProperty("collectionSchemeListTopic")
    private  String collectionSchemeListTopic;

    @JsonProperty("decoderManifestTopic")
    private  String decoderManifestTopic;

    @JsonProperty("canDataTopic")
    private  String canDataTopic;

    @JsonProperty("checkinTopic")
    private  String checkinTopic;

    @JsonProperty("certificateFilename")
    private  String certificateFilename;

    @JsonProperty("privateKeyFilename")
    private  String privateKeyFilename;

   /* public MqttConnectionConfig(@JsonProperty("connectionType")  String connectionType ,
                                @JsonProperty("endpointUrl")  String endPointUrl,
                                @JsonProperty("clientId")  String clientId,
                                @JsonProperty("collectionSchemeListTopic")  String collectionSchemeListTopic,
                                @JsonProperty("decoderManifestTopic")  String decoderManifestTopic,
                                @JsonProperty("canDataTopic")  String canDataTopic,
                                @JsonProperty("checkinTopic")  String checkinTopic,
                                @JsonProperty("certificateFilename")  String certificateFilename,
                                @JsonProperty("privateKeyFilename")  String privateKeyFilename) {
        super();
        this.connectionType = connectionType;
        this.endPointUrl = endPointUrl;
        this.clientId = clientId;
        this.collectionSchemeListTopic = collectionSchemeListTopic;
        this.decoderManifestTopic = decoderManifestTopic;
        this.canDataTopic = canDataTopic;
        this.checkinTopic = checkinTopic;
        this.certificateFilename = certificateFilename;
        this.privateKeyFilename = privateKeyFilename;
    }*/


    /*public final String getEndPointUrl() {
        return this.endPointUrl;
    }

    public final String getConnectionType() {
        return this.connectionType;
    }


    public final String getClientId() {
        return this.clientId;
    }


    public final String getCollectionSchemeListTopic() {
        return this.collectionSchemeListTopic;
    }


    public final String getDecoderManifestTopic() {
        return this.decoderManifestTopic;
    }


    public final String getCanDataTopic() {
        return this.canDataTopic;
    }


    public final String getCheckinTopic() {
        return this.checkinTopic;
    }


    public final String getCertificateFilename() {
        return this.certificateFilename;
    }


    public final String getPrivateKeyFilename() {
        return this.privateKeyFilename;
    }


    public final String component1() {
        return this.endPointUrl;
    }


    public final String component2() {
        return this.clientId;
    }


    public final String component3() {
        return this.collectionSchemeListTopic;
    }


    public final String component4() {
        return this.decoderManifestTopic;
    }


    public final String component5() {
        return this.canDataTopic;
    }


    public final String component6() {
        return this.checkinTopic;
    }


    public final String component7() {
        return this.certificateFilename;
    }


    public final String component8() {
        return this.privateKeyFilename;
    }*/


   /* public final MqttConnectionConfig copy(@JsonProperty("connectionType")  String connectionType,@JsonProperty("endpointUrl")  String endPointUrl, @JsonProperty("clientId")  String clientId, @JsonProperty("collectionSchemeListTopic")  String collectionSchemeListTopic, @JsonProperty("decoderManifestTopic")  String decoderManifestTopic, @JsonProperty("canDataTopic")  String canDataTopic, @JsonProperty("checkinTopic")  String checkinTopic, @JsonProperty("certificateFilename")  String certificateFilename, @JsonProperty("privateKeyFilename")  String privateKeyFilename) {
        Intrinsics.checkNotNullParameter(connectionType, "connectionType");
        Intrinsics.checkNotNullParameter(endPointUrl, "endPointUrl");
        Intrinsics.checkNotNullParameter(clientId, "clientId");
        Intrinsics.checkNotNullParameter(collectionSchemeListTopic, "collectionSchemeListTopic");
        Intrinsics.checkNotNullParameter(decoderManifestTopic, "decoderManifestTopic");
        Intrinsics.checkNotNullParameter(canDataTopic, "canDataTopic");
        Intrinsics.checkNotNullParameter(checkinTopic, "checkinTopic");
        Intrinsics.checkNotNullParameter(certificateFilename, "certificateFilename");
        Intrinsics.checkNotNullParameter(privateKeyFilename, "privateKeyFilename");
        return new MqttConnectionConfig(connectionType,endPointUrl, clientId, collectionSchemeListTopic, decoderManifestTopic, canDataTopic, checkinTopic, certificateFilename, privateKeyFilename);
    }*/


   /* public String toString() {
        return "MqttConnectionConfig(endPointUrl=" + this.endPointUrl + ", clientId=" + this.clientId + ", collectionSchemeListTopic=" + this.collectionSchemeListTopic + ", decoderManifestTopic=" + this.decoderManifestTopic + ", canDataTopic=" + this.canDataTopic + ", checkinTopic=" + this.checkinTopic + ", certificateFilename=" + this.certificateFilename + ", privateKeyFilename=" + this.privateKeyFilename + ')';
    }

    public int hashCode() {
        int result = this.endPointUrl.hashCode();
        result = result * 31 + this.clientId.hashCode();
        result = result * 31 + this.collectionSchemeListTopic.hashCode();
        result = result * 31 + this.decoderManifestTopic.hashCode();
        result = result * 31 + this.canDataTopic.hashCode();
        result = result * 31 + this.checkinTopic.hashCode();
        result = result * 31 + this.certificateFilename.hashCode();
        result = result * 31 + this.privateKeyFilename.hashCode();
        return result;
    }

    public boolean equals( Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof MqttConnectionConfig)) {
            return false;
        } else {
            MqttConnectionConfig var2 = (MqttConnectionConfig)other;
            if (!Intrinsics.areEqual(this.endPointUrl, var2.endPointUrl)) {
                return false;
            } else if (!Intrinsics.areEqual(this.clientId, var2.clientId)) {
                return false;
            } else if (!Intrinsics.areEqual(this.collectionSchemeListTopic, var2.collectionSchemeListTopic)) {
                return false;
            } else if (!Intrinsics.areEqual(this.decoderManifestTopic, var2.decoderManifestTopic)) {
                return false;
            } else if (!Intrinsics.areEqual(this.canDataTopic, var2.canDataTopic)) {
                return false;
            } else if (!Intrinsics.areEqual(this.checkinTopic, var2.checkinTopic)) {
                return false;
            } else if (!Intrinsics.areEqual(this.certificateFilename, var2.certificateFilename)) {
                return false;
            } else {
                return Intrinsics.areEqual(this.privateKeyFilename, var2.privateKeyFilename);
            }
        }
    }*/
}
