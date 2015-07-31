/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices;

import org.junit.Assert;
import org.junit.Test;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public class UpgradesvcTestBase {

    @Test
    public void test() throws Exception {
        String base = "http://localhost:9998/upgrade/";
        // cluster state
        Client client = Client.create();
        WebResource webResource = client
                .resource(base + "cluster-state");
        ClientResponse response = webResource.accept("application/XML").get(
                ClientResponse.class);
        Assert.assertEquals(200, response.getStatus());
        String output = response.getEntity(String.class);
        System.out.println("GET ClusterState: Output from server .... " + output);

        // get targetversion
        webResource = client
                .resource(base + "target-version");
        response = webResource.accept("application/XML").get(
                ClientResponse.class);
        if (response.getStatus() != 200) {
            System.out.println(response.getEntity(String.class));
            throw new RuntimeException("Failed : HTTP error code : "
                    + response.getStatus());
        }
        output = response.getEntity(String.class);
        System.out.println("[2] Output from Server .... " + output);

        // set target version
        webResource = client
                .resource(base + "target-version?version=storageos-1.0.0.0.6666");
        response = webResource.put(ClientResponse.class);
        if (response.getStatus() != 200) {
            System.out.println(response.getEntity(String.class));
            throw new RuntimeException("Failed : HTTP error code : "
                    + response.getStatus());
        }

        webResource = client.resource(base + "image/install?version=storageos-1.0.0.0.6666");
        response = webResource.post(ClientResponse.class);
        if (response.getStatus() != 200) {
            System.out.println(response.getEntity(String.class));
            throw new RuntimeException("Failed : HTTP error code : "
                    + response.getStatus());
        }

        webResource = client.resource(base + "image/remove?version=storageos-1.0.0.0.6666");
        response = webResource.post(ClientResponse.class);
        if (response.getStatus() != 200) {
            System.out.println(response.getEntity(String.class));
            throw new RuntimeException("Failed : HTTP error code : "
                    + response.getStatus());
        }

        // wakeup
        webResource = client
                .resource(base + "internal/interrupt");
        response = webResource.post(ClientResponse.class);
        if (response.getStatus() != 200) {
            throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
        }
    }
}
