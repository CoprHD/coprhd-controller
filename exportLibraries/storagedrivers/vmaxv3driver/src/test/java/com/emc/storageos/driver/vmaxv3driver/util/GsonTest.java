/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.util;

import com.emc.storageos.driver.vmaxv3driver.rest.request.RequestStorageGroupPost;
import com.emc.storageos.driver.vmaxv3driver.rest.request.SloBasedStorageGroupParam;
import com.emc.storageos.driver.vmaxv3driver.rest.request.VolumeAttribute;
import com.emc.storageos.driver.vmaxv3driver.rest.response.Symmetrix;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicStatusLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gang on 6/21/16.
 */
public class GsonTest {

    private static Logger logger = LoggerFactory.getLogger(GsonTest.class);

    @Test
    public void testParse() {
        String jsonString = "{\n" +
            "  \"symmetrix\": [\n" +
            "    {\n" +
            "      \"symmetrixId\": \"000196801612\",\n" +
            "      \"sloCompliance\": {\n" +
            "        \"slo_marginal\": 0,\n" +
            "        \"slo_stable\": 0,\n" +
            "        \"slo_critical\": 0\n" +
            "      },\n" +
            "      \"model\": \"VMAX100K\",\n" +
            "      \"ucode\": \"5977.802.781\",\n" +
            "      \"device_count\": 2390,\n" +
            "      \"local\": true,\n" +
            "      \"virtualCapacity\": {\n" +
            "        \"used_capacity_gb\": 420.42,\n" +
            "        \"total_capacity_gb\": 76102.03\n" +
            "      }\n" +
            "    }\n" +
            "  ],\n" +
            "  \"success\": true\n" +
            "}";
        JsonParser parser = new JsonParser();
        JsonElement json = parser.parse(jsonString);
        JsonObject root = json.getAsJsonObject();
        Boolean success = root.get("success").getAsBoolean();
        logger.info("success = {}", success);
        String nativeId = root.getAsJsonArray("symmetrix").get(0).getAsJsonObject().get("symmetrixId").getAsString();
        logger.info("nativeId = {}", nativeId);
    }

    @Test
    public void deserialize() {
        String jsonString = "{\n" +
            "  \"symmetrix\": [\n" +
            "    {\n" +
            "      \"symmetrixId\": \"000196701035\",\n" +
            "      \"sloCompliance\": {\n" +
            "        \"slo_marginal\": 0,\n" +
            "        \"slo_stable\": 0,\n" +
            "        \"slo_critical\": 0\n" +
            "      },\n" +
            "      \"model\": \"VMAX200K\",\n" +
            "      \"ucode\": \"5977.811.784\",\n" +
            "      \"device_count\": 4836,\n" +
            "      \"local\": false,\n" +
            "      \"virtualCapacity\": {\n" +
            "        \"used_capacity_gb\": 1259.8,\n" +
            "        \"total_capacity_gb\": 43558.59\n" +
            "      }\n" +
            "    }\n" +
            "  ],\n" +
            "  \"success\": true\n" +
            "}";
        // Parse the outer result.
        JsonParser parser = new JsonParser();
        JsonElement json = parser.parse(jsonString);
        JsonObject root = json.getAsJsonObject();
        Boolean success = root.get("success").getAsBoolean();
        logger.info("success = {}", success);
        // Parse the inside "symmetrix" instance.
        Symmetrix bean = new Gson().fromJson(root.getAsJsonArray("symmetrix").get(0), Symmetrix.class);
        Assert.assertEquals(bean.getSymmetrixId(), "000196701035");
        Assert.assertEquals(bean.getSloCompliance().getSlo_stable(), new Integer(0));
        Assert.assertEquals(bean.getModel(), "VMAX200K");
        Assert.assertEquals(bean.getLocal(), Boolean.FALSE);
        Assert.assertEquals(bean.getVirtualCapacity().getTotal_capacity_gb(), new Double(43558.59));
        logger.info("Pass all the assert checks.");
    }

    @Test
    public void testConvertJavaBean2Json() {
        Gson gson = new Gson();
        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 404, "Resource Not Found");
        String json = gson.toJson(statusLine);
        logger.info("StatusLine in JSON: {}", json);
    }

    @Test
    public void testConvertRequest2Json() {
        // Prepare the request bean.
        VolumeAttribute volumeAttribute1 = new VolumeAttribute();
        volumeAttribute1.setVolume_size("100");
        volumeAttribute1.setCapacityUnit("GB");
        SloBasedStorageGroupParam volume1 = new SloBasedStorageGroupParam();
        volume1.setVolumeAttribute(volumeAttribute1);
        volume1.setNum_of_vols(3);
        volume1.setSloId("Silver");
        volume1.setWorkloadSelection("OLTP");
        List<SloBasedStorageGroupParam> volumes = new ArrayList<>();
        volumes.add(volume1);
        VolumeAttribute volumeAttribute2 = new VolumeAttribute();
        volumeAttribute2.setVolume_size("2");
        volumeAttribute2.setCapacityUnit("TB");
        SloBasedStorageGroupParam volume2 = new SloBasedStorageGroupParam();
        volume2.setVolumeAttribute(volumeAttribute2);
        volume2.setNum_of_vols(2);
        volume2.setSloId("Gold");
        volume2.setWorkloadSelection("OLAP");
        volumes.add(volume2);
        RequestStorageGroupPost post = new RequestStorageGroupPost();
        post.setSloBasedStorageGroupParam(volumes);
        post.setSrpId("SRP_1");
        post.setStorageGroupId("test2");
        // Convert the bean to JSON format string.
        String json = new Gson().toJson(post);
        logger.info("Request in JSON: {}", json);
    }

}
