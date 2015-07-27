/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.emc.storageos.vnxe.requests.KHClient;

/*
 *  VNXe (KittyHawk) API client factory
 */
public class VNXeApiClientFactory {
    // client map
    private ConcurrentMap<String, VNXeApiClient> clientMap;
    
    public void init() {
        clientMap = new ConcurrentHashMap<String, VNXeApiClient>();
    }
    
    /*
     * get VnxeApiClient based on the vnxe unisphere info
     */
    public VNXeApiClient getClient(String host, int port, String user, String password){
        StringBuilder builder = new StringBuilder();
        builder.append(host);
        builder.append("_");
        builder.append("port");
        builder.append("_");
        builder.append("user");
        builder.append("_");
        builder.append("password");
        String key = builder.toString();
        VNXeApiClient apiClient = null;
        if (clientMap.get(key) != null) {
            apiClient = clientMap.get(key);
        } else {
            KHClient client = new KHClient(host, port, user, password);
            apiClient = new VNXeApiClient(client);
            clientMap.putIfAbsent(key, apiClient);
        }
        return apiClient;
    }
    
}
