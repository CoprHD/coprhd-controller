/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.property;


import java.util.Map;

import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.TreeMap;
import java.util.regex.Pattern;

@XmlRootElement(name = "property_base_info")
public class PropertyInfo {

    private Map<String, String> properties;
    
    public static enum PropCategory { ALL,
    	                              CONFIG, 
    	                              OVF, 
    	                              MUTATED,
                                      SECRETS,
    	                              OBSOLETE,
                                      REDEPLOY
    }

    /**
     * Default constructor
     */
    public PropertyInfo() { }

    /**
     * Constructor
     * @param properties    map containing key,value pairs
     */
    public PropertyInfo(Map<String, String> properties) {
        if (properties != null) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                getProperties().put(entry.getKey(), entry.getValue());
            }
        }  	
    }
    
    @XmlElementWrapper(name = "properties")
    public Map<String, String> getProperties() {
        if (properties == null) {
            properties = new TreeMap<String, String>();
        }
        return properties;
    }
    
    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    /**
     * Get a specified property
     * @param name  property name
     * @return      property value
     */
    public String getProperty(String name) {
        return getProperties().get(name);
    }

    /**
     * Get all properties
     * @return  map containing key, value pair
     *//**
     * Get all properties
     * @return  map containing key, value pair
     *//**
     * Get all properties
     * @return  map containing key, value pair
     *//**
     * Get all properties
     * @return  map containing key, value pair
     */
    public Map<String, String> getAllProperties() {
        return getProperties();
    }
     
}
