/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.model.property;

import org.codehaus.jackson.annotate.JsonIgnore;

import javax.xml.bind.annotation.XmlRootElement;

import java.util.TreeMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PropertyInfoExt is only published as a shared object
 * According to CoordinatorClassInfo's requirement, only id, kind are necessary.
 * To comply with other similar classes, we gave it a dummy attribute as "targetInfoExt"
 */
@XmlRootElement(name = "property_info")
public class PropertyInfoRestRep extends PropertyInfo {
    public static final String CONFIG_VERSION  = "config_version";
    public static final String CONNECTEMC_TRANSPORT = "system_connectemc_transport";
    
    public PropertyInfoRestRep() {}

    public PropertyInfoRestRep(Map<String, String> properties) {
        super(properties);
    }
  
    public Map<String, String> getAllProperties() {
        return super.getAllProperties();
    }

    public String getProperty(String name) {
        return super.getProperty(name);
    }

    public void addProperties(Map<String, String> map) {
        if (map != null) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                getProperties().put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Compare properties' values
     * Return true if common properties' value have been changed or symmetric difference is not none
     *
     * @param object
     * @return true if different; otherwise false
     */
    public boolean diff(PropertyInfoRestRep object) {
        if (object != null) {
            // compare intersection properties' value
            for (Map.Entry<String, String> entry : getProperties().entrySet()) {
                final String key = entry.getKey();
                final String value = object.getProperty(key) ;
                if (value == null || value != null && !value.equals(entry.getValue())) {
                    return true;
                }
            }

            // check if any property that is in object but not in _properties
            for (Map.Entry<String, String> entry : object.getAllProperties().entrySet()) {
                final String key = entry.getKey();

                if (!getProperties().containsKey(key)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Get the properties which values are different in object and this._properties.
     *
     * @param object
     * @return map of different properties' name and value
     */
    public Map<String, String> getDiffProperties(PropertyInfoRestRep object) {
        Map<String, String> diffProps = new TreeMap<String, String>();

        if (object != null) {
            for (Map.Entry<String, String> entry : getProperties().entrySet()) {
                final String key = entry.getKey();
                final String value = entry.getValue();

                final String objectVal = object.getProperty(key) ;
                if (objectVal == null || objectVal != null && !objectVal.equals(value)) {
                    diffProps.put(key, value);
                }
            }

            for (Map.Entry<String, String> entry : object.getAllProperties().entrySet()) {
                final String key = entry.getKey();
                final String value = entry.getValue();

                if (!getProperties().containsKey(key)) {
                    diffProps.put(key, value);
                }
            }
        }

        return diffProps;
    }
    
    public void removeProperty(String propName) {
        getProperties().remove(propName);
    }

    public void removeProperties(final Set<String> props) {
        if (props != null) {
            for (String key : props) {
                getProperties().remove(key);
            }
        }
    }

    public void removeProperties(final List<String> props) {
        if (props != null) {
            for (String key : props) {
                getProperties().remove(key);
            }
        }
    }

    public void addProperty(String propName, String propValue) {
        getProperties().put(propName, propValue);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return getProperties().isEmpty();
    }

}

