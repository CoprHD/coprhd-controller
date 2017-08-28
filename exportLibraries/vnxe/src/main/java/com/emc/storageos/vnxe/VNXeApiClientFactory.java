/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe;

import java.net.URI;

import com.emc.storageos.services.restutil.RestClientFactory;
import com.emc.storageos.services.restutil.RestClientItf;
import com.emc.storageos.vnxe.requests.KHClient;
import com.sun.jersey.api.client.Client;


/*
 *  VNXe (KittyHawk) API client factory
 */
public class VNXeApiClientFactory extends RestClientFactory{
    /*
     * get VnxeApiClient based on the vnxe unisphere info
     */
    public VNXeApiClient getClient(String host, int port, String user, String password) {
        KHClient client = new KHClient(host, port, user, password, _clientHandler, false);
        VNXeApiClient apiClient = new VNXeApiClient(client);
        apiClient.isFASTVPEnabled();
        return apiClient;
    }

    public VNXeApiClient getUnityClient(String host, int port, String user, String password) {
        KHClient client = new KHClient(host, port, user, password, _clientHandler, true);
        VNXeApiClient apiClient = new VNXeApiClient(client);
        apiClient.isFASTVPEnabled();
        return apiClient;
    }
 
   @Override
   protected RestClientItf createNewRestClient(URI endpoint, String username,
         String password, Client client) {
     return null;
   }
   
   


}
