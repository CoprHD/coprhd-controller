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
package com.emc.storageos.plugins;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StorageSystemViewObject {

    static public final String STORAGE_NAME = "storage_name";
    static public final String MODEL = "model";
    static public final String SERIAL_NUMBER = "serial_number";
    static public final String VERSION = "version";

    private Set<String> _providers;

    private String _deviceType;

    private Map<String, String> _properties = new HashMap<String, String>();

    public Set<String> getProviders() {
        return _providers;
    }

    public void addprovider(String provider) {
        if (_providers == null) {
            _providers = new HashSet<String>();
        }
        _providers.add(provider);
    }

    public String getDeviceType() {
        return _deviceType;
    }

    public void setDeviceType(String deviceType) {
        _deviceType = deviceType;
    }

    public void setProperty(String key, String value) {
        _properties.put(key, value);
    }

    public String getProperty(String key) {
        return _properties.get(key);
    }
}
