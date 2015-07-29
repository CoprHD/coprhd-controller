/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog.impl;

import com.emc.vipr.client.ClientConfig;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.catalog.ApiList;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.util.List;

public class ApiListUtils {
    public static <T> List<T> postApiList(RestClient client, Object request, GenericType<List<T>> type, String path, Object... args) {
        List<T> apiList;
        if (isXmlType(client.getConfig())) {
            apiList = client.post(ApiList.class, request, path, args).getList();
        }
        else {
            apiList = client.post(type, request, path, args);
        }
        return ResourceUtils.defaultList(apiList);
    }

    public static <T> List<T> getApiList(RestClient client, GenericType<List<T>> type, String path, Object... args) {
        List<T> apiList;
        if (isXmlType(client.getConfig())) {
            apiList = client.get(ApiList.class, path, args).getList();
        }
        else {
            apiList = client.get(type, path, args);
        }
        return ResourceUtils.defaultList(apiList);
    }

    public static <T> List<T> getApiListUri(RestClient client, GenericType<List<T>> type, URI uri) {
        List<T> apiList;
        if (isXmlType(client.getConfig())) {
            apiList = client.getURI(ApiList.class, uri).getList();
        }
        else {
            apiList = client.getURI(type, uri);
        }
        return ResourceUtils.defaultList(apiList);
    }

    public static <T> List<T> getEntityList(ClientConfig config, GenericType<List<T>> type, ClientResponse response) {
        List<T> apiList;
        if (isXmlType(config)) {
            apiList = response.getEntity(ApiList.class).getList();
        }
        else {
            apiList = response.getEntity(type);
        }
        return ResourceUtils.defaultList(apiList);
    }

    private static boolean isXmlType(ClientConfig config) {
        return config.getMediaType().equals(MediaType.APPLICATION_XML);
    }
}
