/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation 
 * All Rights Reserved 
 *
 * This software contains the intellectual property of EMC Corporation 
 * or is licensed to EMC Corporation from third parties.  Use of this 
 * software and the intellectual property contained therein is expressly 
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.apidiff.model;

import com.emc.storageos.apidiff.Constants;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Immutable Class to save API info of one rest service, such as api service, system management service.
 */
public class ServiceCatalog {

    private final Map<ApiIdentifier, ApiDescriptor> apiMap;
    private final Map<String, String> elementMap;
    private final String serviceName;
    private final String version;

    public ServiceCatalog(Map<ApiIdentifier, ApiDescriptor> apiMap, Map<String, String> elementMap,
            String serviceName, String version) {
        if (apiMap == null)
            this.apiMap = new HashMap<ApiIdentifier, ApiDescriptor>();
        else
            this.apiMap = apiMap;
        if (elementMap == null)
            this.elementMap = new HashMap<String, String>();
        else
            this.elementMap = elementMap;
        this.serviceName = serviceName;
        this.version = version;
        filter();
    }

    public ServiceCatalog(Map<ApiIdentifier, ApiDescriptor> apiMap, String serviceName, String version) {
        this(apiMap, null, serviceName, version);
    }

    public ServiceCatalog(String serviceName, String version) {
        this(null, null, serviceName, version);
    }

    public void filter() {
        Iterator<Map.Entry<ApiIdentifier, ApiDescriptor>> apiMapIter = apiMap.entrySet().iterator();
        while (apiMapIter.hasNext()) {
            Map.Entry<ApiIdentifier, ApiDescriptor> entry = apiMapIter.next();
            String path = entry.getKey().getPath().split(Constants.URL_PATH_SEPARATOR)[1];
            if (Constants.INTERNAL_API.equals(path))
                apiMapIter.remove();
        }
    }

    public Map<ApiIdentifier, ApiDescriptor> getApiMap() {
        return apiMap;
    }

    public Map<String, String> getElementMap() {
        return elementMap;
    }

    public String getVersion() {
        return version;
    }

    public String getServiceName() {
        return serviceName;
    }

    /**
     * Clean useless elements to reduce memory usage and keep consistency
     */
    public void update() {
        if (elementMap == null)
            return;

        if (apiMap == null || apiMap.isEmpty()) {
            elementMap.clear();
            return;
        }

        Iterator<String> elementIterator = elementMap.keySet().iterator();
        while (elementIterator.hasNext()) {
            String elementName = elementIterator.next();
            boolean found = false;
            for (ApiDescriptor apiResource : apiMap.values()) {
                if ((apiResource.getRequestElement() != null
                        && apiResource.getRequestElement().equals(elementName))
                        || (apiResource.getResponseElement() != null
                        && apiResource.getResponseElement().equals(elementName)))
                    found = true;
            }
            if (!found)
                elementIterator.remove();
        }
    }
}
