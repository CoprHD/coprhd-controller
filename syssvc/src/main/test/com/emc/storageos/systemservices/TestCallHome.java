/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import javax.ws.rs.core.MediaType;

import java.io.File;

import static org.junit.Assert.assertTrue;

public class TestCallHome {
    public static final String TMP_VERSION_FILE = "/opt/storageos/conf/version.properties";
    private static volatile Client client = null;

    @Before
    public void setUp() {
        client = Client.create();
    }

    @Test
    public void testSendAlertEvent() throws Exception {
        String body = "{ \"nodeid\":1, " +
                "\"start\":\"2012-08-01_09:55:53\"," +
                "\"logName\":\"controllersvc\"," +
                "\"severity\":\"6\"," +
                "\"end\":\"2012-08-10_09:58:00\", " +
                "\"userStr\" : \"testString\" }";

        ClientResponse response = client.resource("http://localhost:9998/syssvc/event/genAlertEvent")
                .type(MediaType.APPLICATION_JSON).post(ClientResponse.class, body);

        JSONObject jResp = response.getEntity(JSONObject.class);
        if (jResp.has("Description")) {
            assertTrue(true);
        } else {
            assertTrue(false);
        }
    }
}
