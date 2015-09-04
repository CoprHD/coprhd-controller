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
package com.emc.storageos.coordinator.client.model;

import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Product name class
 *
 * Product name is initialized by bean file. It is used
 * SoftwareVersion and the whole upgrade machinery depends on this name
 * e.g. "vipr"
 */
public class ProductName {
    private static String _name;

    protected ProductName() {
    }

    public void setName(String name) {
        _name = name;
    }

    public static String getName() {
        if (_name == null) {
            throw APIException.internalServerErrors.targetIsNullOrEmpty("product name");
        }
        return _name;
    }
}
