/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

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

}
