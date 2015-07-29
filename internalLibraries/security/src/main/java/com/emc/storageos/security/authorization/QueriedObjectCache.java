/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.authorization;

import com.emc.storageos.db.client.model.*;

import java.net.URI;
import java.util.HashMap;

/**
 * Class for caching objects to thread local
 */
public class QueriedObjectCache {
    private static final String ROOT_TENANT_KEY = "ROOT_TENANT";
    // a safety net in case the thread local cache is abused, like in CQ607847
    private static final int MAX_LIMIT = 4096;

    /**
     * ThreadLocal - HashMap, maps URI/String to a DataObject
     */
    private static final ThreadLocal QUERIED_OBJECT_MAP = new ThreadLocal() {
        protected Object initialValue() {
            return new HashMap<String, DataObject>();
        }
    };

    /**
     * Returns thread local map instance
     * 
     * @return
     */
    private static HashMap<String, DataObject> get() {
        return (HashMap<String, DataObject>) QUERIED_OBJECT_MAP.get();
    }

    /**
     * Add an object into the map
     * 
     * @param object DataObject to add to map
     */
    public static void setObject(DataObject object) {
        if (get().size() < MAX_LIMIT) {
            get().put(object.getId().toString(), object);
        }
    }

    /**
     * get an object from the map
     * 
     * @param id URI of the object
     * @clazz DataObject type
     */
    public static <T extends DataObject> T getObject(URI id, Class<T> clazz) {
        return (clazz.cast(get().get(id.toString())));
    }

    /**
     * Save root tenant object into map
     * 
     * @param tenant
     */
    public static void setRootTenantObject(TenantOrg tenant) {
        if (get().size() < MAX_LIMIT) {
            get().put(ROOT_TENANT_KEY, tenant);
        }
    }

    /**
     * Get root tenant object
     * 
     * @return TenantOrg object for root tenant
     */
    public static TenantOrg getRootTenantOrgObject() {
        return (TenantOrg) get().get(ROOT_TENANT_KEY);
    }

    public static void clearCache() {
        get().clear();
    }
}
