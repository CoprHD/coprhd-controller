/*
 * Copyright (c) 2011-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service;

import com.emc.storageos.api.service.ApiTestBase.BalancedWebResource;
import com.sun.jersey.api.client.ClientResponse;
import org.junit.Assert;
import org.springframework.util.CollectionUtils;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * A class that is used to clean up created resources like authnprovider, tenant, etc
 * upon the completion of each tests.
 * 
 */
public class CleanupResource {
    private String _method;
    private String _url;
    private BalancedWebResource _user;
    private Object _requestParam;
    private int _expectedStatus;

    public CleanupResource(String method, String url, BalancedWebResource user, Object requestParam) {
        _method = method;
        _url = url;
        _user = user;
        _requestParam = requestParam;
        _expectedStatus = 200;
    }

    public CleanupResource(String method, String url, BalancedWebResource user, Object requestParam, int expectedStatus) {
        _method = method;
        _url = url;
        _user = user;
        _requestParam = requestParam;
        _expectedStatus = expectedStatus;
    }

    // Function that iterates the list in reverse order and cleans up all the registered
    // resources. Doing it in reverse order to make the cleanup success (the first created
    // resource should be cleaned at last).
    public static void cleanUpTestResources(LinkedList<CleanupResource> cleanupResourcetList) {
        if (!CollectionUtils.isEmpty(cleanupResourcetList)) {
            Iterator<CleanupResource> reverseItr = cleanupResourcetList.descendingIterator();
            while (reverseItr.hasNext()) {
                CleanupResource cleanupResource = reverseItr.next();
                if (cleanupResource._method.equalsIgnoreCase("delete")) {
                    ClientResponse response = cleanupResource._user.path(cleanupResource._url).delete(ClientResponse.class);
                    Assert.assertEquals(cleanupResource._expectedStatus, response.getStatus());
                } else if (cleanupResource._method.equalsIgnoreCase("put")) {
                    ClientResponse response = cleanupResource._user.path(cleanupResource._url).put(ClientResponse.class,
                            cleanupResource._requestParam);
                    Assert.assertEquals(cleanupResource._expectedStatus, response.getStatus());
                } else if (cleanupResource._method.equalsIgnoreCase("post")) {
                    ClientResponse response = cleanupResource._user.path(cleanupResource._url).post(ClientResponse.class,
                            cleanupResource._requestParam);
                    Assert.assertEquals(cleanupResource._expectedStatus, response.getStatus());
                }
            }
        }
    }
}
