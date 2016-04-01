package com.emc.storageos.netapp;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class NetappApiFactory {

    private ConcurrentMap<String, NetAppApi> clientMap;

    public void init() {
        clientMap = new ConcurrentHashMap<String, NetAppApi>();
    }

    public NetAppApi getClient(String host, int port, String user, String password, Boolean https, String vFiler) {
        StringBuilder builder = new StringBuilder();
        builder.append(host);

        String key = builder.toString();
        NetAppApi apiClient = null;
        if (clientMap.get(key) != null) {
            apiClient = clientMap.get(key);
        } else {
            apiClient = new NetAppApi.Builder(host, port, user, password).https(https).vFiler(vFiler).build();
            clientMap.putIfAbsent(key, apiClient);
        }
        return apiClient;
    }

}
