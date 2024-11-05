package com.amazonaws.iot.fleetwise.vehiclesimulator.client;

import com.amazonaws.iot.fleetwise.vehiclesimulator.util.Utils;
import software.amazon.awssdk.services.iotfleetwise.IoTFleetWiseClient;
import software.amazon.awssdk.services.iotfleetwise.model.*;

import java.util.List;

public class DecoderManifest {
    public static void main(String[] args) {
        IoTFleetWiseClient client = IoTFleetWiseClient.builder()
                .build();

        String modelManifestArn = "arn:aws:iotfleetwise:us-east-1:034352053400:model-manifest/blog-modelmanifest-01";
        String decoderManifestName = "blog-decodermanifest-01";
        String decoderManifestDescription = "My decoder manifest description";
        String jsonFile = "./fw/decoder-manifest1.json";
        String status = "ACTIVE";
        updateDecoderManifest(client,status);

        //CreateDecoderManifestResponse response = createDecoderManifest(jsonFile, modelManifestArn, decoderManifestName, decoderManifestDescription, client);
        //System.out.println("Decoder manifest created: " + response.arn());
    }

    private static CreateDecoderManifestResponse createDecoderManifest(String jsonFile, String modelManifestArn, String decoderManifestName, String decoderManifestDescription, IoTFleetWiseClient client) {
        List<NetworkInterface> networkInterfaces = Utils.parseNetworkInterfaceFromJson(jsonFile);
        List<SignalDecoder> signalDecoders =Utils.parseSignalDecoderFromJson(jsonFile);
        CreateDecoderManifestRequest request = CreateDecoderManifestRequest.builder()
                .modelManifestArn(modelManifestArn)
                .name(decoderManifestName)
                .description(decoderManifestDescription)
                .networkInterfaces(networkInterfaces)
                .signalDecoders(signalDecoders)
                .build();
        return client.createDecoderManifest(request);
    }


    private static GetDecoderManifestResponse getDecoderManifest(IoTFleetWiseClient client, String modelManifestArn) {
         GetDecoderManifestRequest getModelManifestRequest = GetDecoderManifestRequest.builder()
                .name("blog-modelmanifest-01")
                .build();

        return client.getDecoderManifest(getModelManifestRequest);
    }


    private static DeleteDecoderManifestResponse deleteDecoderManifest(IoTFleetWiseClient client, String modelManifestArn) {
        DeleteDecoderManifestRequest getModelManifestRequest = DeleteDecoderManifestRequest.builder()
                .name("blog-modelmanifest-01")
                .build();

        return client.deleteDecoderManifest(getModelManifestRequest);
    }

    private static UpdateDecoderManifestResponse updateDecoderManifest(IoTFleetWiseClient client,
                                                                       String status) {
        UpdateDecoderManifestRequest updateModelManifestRequest = UpdateDecoderManifestRequest.builder()
                .name("blog-decodermanifest-01")
                .status(status)
                .build();

        return client.updateDecoderManifest(updateModelManifestRequest);
    }
}

