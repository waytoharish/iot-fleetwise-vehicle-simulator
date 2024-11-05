package com.amazonaws.iot.fleetwise.vehiclesimulator.client;



import com.amazonaws.iot.fleetwise.vehiclesimulator.util.Utils;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import software.amazon.awssdk.services.iotfleetwise.IoTFleetWiseClient;
import software.amazon.awssdk.services.iotfleetwise.model.*;

import java.util.List;

public class Campaign {

    public static void main(String[] args) throws JsonProcessingException {
        IoTFleetWiseClient client = IoTFleetWiseClient.builder().build();
        String jsonFile = "./fw/continus-campaign.json";
        String jsonContent = Utils.readJsonFile(jsonFile);
        //CollectionScheme collectionScheme = Utils.collectionScheme(jsonFile);

        //String arn = createConditionalCampaign(client, jsonContent);
        updateCampaign(client);



       // System.out.println(convertedObject1.get("name"));

      /*  JsonObject jsonObject = JsonParser.parseString(Utils.readJsonFile(jsonFile))
                .getAsJsonObject();

        String name =jsonObject.get("name").toString();*/
      //  System.out.println(arn);

        //getCampaign(client);
       // System.out.println("arn value   "+arn);
    }

    private static String createConditionalCampaign(IoTFleetWiseClient client, String jsonFile) {

        CreateCampaignRequest request = CreateCampaignRequest.builder()
                .name(Utils.getvalue(jsonFile,"name"))
                .signalCatalogArn(Utils.getvalue(jsonFile,"signalCatalogArn"))
                .targetArn(Utils.getvalue(jsonFile,"targetArn"))
                .collectionScheme(Utils.collectionScheme(jsonFile))
                .signalsToCollect(Utils.signalToCollectFromJson(jsonFile))
                .diagnosticsMode(Utils.getvalue(jsonFile,"diagnosticsMode"))
                .postTriggerCollectionDuration(0l)
                .diagnosticsMode("OFF")
                .dataDestinationConfigs(Utils.dataDestinationConfigFromJson(jsonFile))
              //  .dataExtraDimensions("VehicleVIN")
                //.spoolingMode("spoolingMode")
                .compression("SNAPPY")
                .build();

        CreateCampaignResponse response = client.createCampaign(request);
        System.out.println("Campaign ARN: " + response.arn());
        return null;
    }

    private static String createContinuousCampaign(IoTFleetWiseClient client, String jsonFile) {
        CollectionScheme collectionScheme=Utils.collectionScheme(jsonFile);
        CreateCampaignRequest request = CreateCampaignRequest.builder()
                .name("TestCampign")
                .collectionScheme(collectionScheme)
                .signalsToCollect(Utils.signalToCollectFromJson(jsonFile))
                .diagnosticsMode("")
                .postTriggerCollectionDuration(0l)
                .dataExtraDimensions("VehicleVIN")
                .spoolingMode("spoolingMode")
                .compression("SNAPPY")
                .build();
        CreateCampaignResponse response = client.createCampaign(request);
        System.out.println("Campaign ARN: " + response.arn());
        return  response.arn();
    }

    private static String updateCampaign(IoTFleetWiseClient client) {
        UpdateCampaignRequest request = UpdateCampaignRequest.builder()
                .name("conditional-snapshot-campaign")
                .action("APPROVE")
                .build();

       UpdateCampaignResponse response = client.updateCampaign(request);
        System.out.println("Campaign ARN: " + response.arn());
        return  response.arn();
    }

    private static String deleteCampaign(IoTFleetWiseClient client) {
        DeleteCampaignRequest request = DeleteCampaignRequest.builder()
                .name("TestCampign")
                .build();
        DeleteCampaignResponse response = client.deleteCampaign(request);
        return  response.arn();
    }

    private static void getCampaign(IoTFleetWiseClient client) throws JsonProcessingException {
        GetCampaignRequest request = GetCampaignRequest.builder()
                .name("highFidelityVehicleData")
                .build();
        GetCampaignResponse response = client.getCampaign(request);


        System.out.println("json     "+response.targetArn());

    }
}
