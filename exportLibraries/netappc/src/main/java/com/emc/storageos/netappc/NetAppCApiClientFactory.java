/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.netappc;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 
 * NetApp Cluster Api client Factory
 *
 */
public class NetAppCApiClientFactory {

    private ConcurrentMap<String, NetAppClusterApi> clientMap;

    public void init() {
        clientMap = new ConcurrentHashMap<String, NetAppClusterApi>();
    }

    /**
     * Return NetAppCluster API client, create if not present
     * 
     * @param host
     * @param port
     * @param user
     * @param password
     * @param https
     * @param vserver
     * @return
     */
    public NetAppClusterApi getClient(String host, int port, String user, String password, Boolean https, String vserver) {
        StringBuilder builder = new StringBuilder();
        builder.append(host);

        String key = builder.toString();
        NetAppClusterApi apiClient = null;
        if (clientMap.get(key) != null) {
            apiClient = clientMap.get(key);
        } else {
            apiClient = new NetAppClusterApi.Builder(host,
                    port, user, password).https(https).svm(vserver).build();
        }
        return apiClient;
    }

}
