/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.model.usergroup;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * API payload for user groups creation.
 */

public class UserAttributeParam {
    private static final Logger _log = LoggerFactory.getLogger(UserGroupCreateParam.class);

    private String key;
    private Set<String> values;

    public UserAttributeParam() {}

    public UserAttributeParam(String key, Set<String> values) {
        this.key = key;
        this.values = values;
    }

    @XmlElement(required=true, name="key")
    @JsonProperty("key")
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @XmlElementWrapper(name="values")
    @XmlElement(required=true, name="value")
    @JsonProperty("values")
    public Set<String> getValues() {
        if (values == null) {
            values = new HashSet<String>();
        }
        return values;
    }

    public void setValues(Set<String> values) {
        this.values = values;
    }

    @Override
    public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (IOException e) {
            _log.error("Failed to convert user attribute param to string.", e);
        }
        return null;
    }

    public static UserAttributeParam fromString(String userMappingString) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(userMappingString, UserAttributeParam.class);
        } catch (IOException e) {
            _log.error("Failed to convert user attribute param from string.", e);
        }
        return null;
    }

    /***
     * Compare to find if the given user attribute param matches/subset or not.
     *
     * @param attributeParam to compared.
     * @return true if the attributeParam matches with this.
     */
    public boolean containsAllAttributeValues (UserAttributeParam attributeParam) {
        boolean containsAllAttributes = false;

        if (attributeParam == null) {
            _log.warn("Invalid user attribute param");
            return containsAllAttributes;
        }

        _log.debug("Comparing attributes {}, {}", attributeParam.toString(), this.toString());

        if (!getKey().equalsIgnoreCase(attributeParam.getKey())) {
            _log.debug("Attribute key {} does not match with {}", getKey(), attributeParam.getKey());
            return containsAllAttributes;
        }

        if (getValues() == null || getValues().isEmpty() ||
                attributeParam.getValues() == null || attributeParam.getValues().isEmpty()) {
            _log.debug("Empty attribute values to compare. attributes {}, comparing attributes {}",
                    getValues(), attributeParam.getValues());
            return containsAllAttributes;
        }

        //Set the containsAllAttributes as true here and mark as false if one of the comparing attribute value
        //is not found in the object's attribute value list.
        containsAllAttributes = true;

        for (String comparingValue : attributeParam.getValues()) {
            boolean foundValue = false;
            for (String value : getValues()) {
                if (value != null &&
                        comparingValue != null &&
                        comparingValue.equalsIgnoreCase(value)) {
                    foundValue = true;
                    break;
                }
            }
            if (!foundValue) {
                containsAllAttributes = false;
                break;
            }
        }

        return containsAllAttributes;
    }

    /***
     * Compare to find if the given user attribute param matches/subset or not.
     *
     * @param attributeParam to compared.
     * @return true if the attributeParam matches with this.
     */
    public boolean containsOverlappingAttributeValues (UserAttributeParam attributeParam) {
        boolean containsOverlappingAttributes = false;

        if (attributeParam == null) {
            _log.warn("Invalid user attribute param");
            return containsOverlappingAttributes;
        }

        _log.debug("Comparing attributes {}, {}", attributeParam.toString(), this.toString());

        if (!getKey().equalsIgnoreCase(attributeParam.getKey())) {
            _log.debug("Attribute key {} does not match with {}", getKey(), attributeParam.getKey());
            return containsOverlappingAttributes;
        }

        if (getValues() == null || getValues().isEmpty() ||
                attributeParam.getValues() == null || attributeParam.getValues().isEmpty()) {
            _log.debug("Empty attribute values to compare. attributes {}, comparing attributes {}",
                    getValues(), attributeParam.getValues());
            return containsOverlappingAttributes;
        }

        for (String comparingValue : attributeParam.getValues()) {
            for (String value : getValues()) {
                if (value != null &&
                        comparingValue != null &&
                        comparingValue.equalsIgnoreCase(value)) {
                    containsOverlappingAttributes = true;
                    break;
                }
            }
        }

        return containsOverlappingAttributes;
    }

    /***
     * Compare to find if the given user attribute param matches or not.
     *
     * @param attributeParam to compared.
     * @return true if the attributeParam matches with this.
     */
    public boolean isEqual(UserAttributeParam attributeParam) {
        boolean isEqual = false;

        if (attributeParam == null) {
            _log.warn("Invalid user attribute param");
            return isEqual;
        }

        if (getValues() == null || getValues().isEmpty() &&
                attributeParam.getValues() == null || attributeParam.getValues().isEmpty()) {
            _log.debug("Empty attribute values to compare. attributes {}, comparing attributes {}",
                    getValues(), attributeParam.getValues());
            return isEqual;
        }

        if (getValues().size() != attributeParam.getValues().size()) {
            _log.debug("Attribute values size {} does not match with {}", getValues().size(), attributeParam.getValues().size());
            return isEqual;
        }

        //Now compare if all the attribute values natches with each other.
        //If they match, both the objects are same, as we already compared the
        //size of the values.
        isEqual = containsAllAttributeValues(attributeParam);

        return isEqual;
    }
}
