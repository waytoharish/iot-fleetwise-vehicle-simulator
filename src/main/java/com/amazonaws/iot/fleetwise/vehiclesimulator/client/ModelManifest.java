package com.amazonaws.iot.fleetwise.vehiclesimulator.client;
import com.amazonaws.iot.fleetwise.vehiclesimulator.util.Utils;
import software.amazon.awssdk.services.iotfleetwise.IoTFleetWiseClient;
import software.amazon.awssdk.services.iotfleetwise.model.*;

import java.util.List;


public class ModelManifest {
    public static void main(String[] args) {
        IoTFleetWiseClient client = IoTFleetWiseClient.builder()
                 .build();
        String jsonFile = "./fw/vehicle-model1.json";
        String modelName ="blog-modelmanifest-01";
        //CreateModelManifestResponse response = createModelManifest(jsonFile, client, modelName);
        getModelManifest(client,modelName);
       // updateModelManifest(client);
       // deleteModelManifest(client, modelName);
        System.out.println("Model manifest created: " +  getModelManifest(client,modelName).arn());
    }



    private static CreateModelManifestResponse createModelManifest(String jsonFile, IoTFleetWiseClient client, String modelName) {
        List<String> nodes = Utils.parseStringFromJson(jsonFile);
        CreateModelManifestRequest request = CreateModelManifestRequest.builder()
                .name("blog-modelmanifest-02")
                .signalCatalogArn("arn:aws:iotfleetwise:us-east-1:034352053400:signal-catalog/main-signal-catalog")
                .nodes(nodes)
                .build();

        return client.createModelManifest(request);
    }

    private static UpdateModelManifestResponse updateModelManifest(IoTFleetWiseClient client, String modelName) {
        UpdateModelManifestRequest request = UpdateModelManifestRequest.builder()
                .name("blog-modelmanifest-01")
                .status("ACTIVE")
                .build();

        return client.updateModelManifest(request);
    }

    private static DeleteModelManifestResponse deleteModelManifest(IoTFleetWiseClient client, String modelName) {
        DeleteModelManifestRequest request = DeleteModelManifestRequest.builder()
                .name("blog-modelmanifest-01")
                .build();

        return client.deleteModelManifest(request);
    }

    private static GetModelManifestResponse getModelManifest(IoTFleetWiseClient client, String modelName) {
        GetModelManifestRequest request = GetModelManifestRequest.builder()
                .name("blog-modelmanifest-01")
                .build();

        return client.getModelManifest(request);
    }


}

