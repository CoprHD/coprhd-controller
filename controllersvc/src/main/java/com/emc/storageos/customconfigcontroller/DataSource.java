/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.customconfigcontroller;

import java.util.HashMap;
import java.util.Map;

/**
 * This class is a data holder that is used to wrap the different
 * data source properties. Functions in the controller layer that
 * wish to generate a custom name create an instance of this class,
 * add the context (data source properties into it) before calling
 * the controller service to generate the name.
 * 
 * 
 */
public class DataSource {
    private Map<String, String> properties;

    /**
     * Returns a map of property-name-to-property-value. Note the
     * property name is expected to be the display name
     * of a {@link DataSourceVariable}.
     * 
     * @return a map of property-name-to-property-value.
     */
    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public void addProperty(String property, String value) {
        if (properties == null) {
            properties = new HashMap<String, String>();
        }
        properties.put(property, value);
    }

    /**
     * It can be improved by casting the property value to a given type
     * 
     * @param sourceClz
     * @param property
     * @return
     */
    public String getPropertyValue(String property) {
        if (properties.containsKey(property)) {
            return properties.get(property);
        }
        return null;
    }
}
