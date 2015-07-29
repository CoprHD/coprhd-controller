/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.tenant;

import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class UserMappingAttributeParam {
    private String key;
    private List<String> values;

    public UserMappingAttributeParam() {
    }

    public UserMappingAttributeParam(String key, List<String> values) {
        this.key = key;
        this.values = this.removeDuplicate(values);
    }

    /**
     * 
     * Lookup string for this key-value pair
     * 
     * @valid none
     */
    @XmlElement(required = true, name = "key")
    @JsonProperty("key")
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    /**
     * 
     * Lookup result for this key-value pair
     * 
     * @valid none
     */
    @XmlElement(required = true, name = "value")
    public List<String> getValues() {
        if (values == null) {
            values = new ArrayList<String>();
        }
        return values;
    }

    public void setValues(List<String> values) {
        this.values = removeDuplicate(values);
    }

    /**
     * Removes the duplicate entries from the collection (List<T>)
     * and returns the list with unique entries.
     * 
     * @valid none
     */
    private <T> List<T> removeDuplicate(List<T> listWithDuplicates) {
        List<T> uniqueList = new ArrayList<T>(new LinkedHashSet<T>(listWithDuplicates));
        return uniqueList;
    }

    @Override
    /**
     * Overridden equals method from Object for the specific purpose
     * of using the HashSet collection.
     * Compares the individual properties of two objects.
     */
    public boolean equals(Object obj) {
        boolean isEqual = false;

        if (obj != null && obj instanceof UserMappingAttributeParam) {
            if (this == obj) {
                isEqual = true;
            }
            else if (key != null &&
                    ((UserMappingAttributeParam) obj).key != null &&
                    !key.equals(((UserMappingAttributeParam) obj).key)) {
                isEqual = false;
            }
            else if (values != null &&
                    ((UserMappingAttributeParam) obj).values != null &&
                    values.size() != ((UserMappingAttributeParam) obj).values.size()) {
                isEqual = false;
            }
            else {
                if (values != null && ((UserMappingAttributeParam) obj).values != null) {
                    isEqual = values.equals(((UserMappingAttributeParam) obj).values);
                } else {
                    isEqual = false;
                }
            }
        }

        return isEqual;
    }

    @Override
    /**
     * Overridden hashCode method from Object for the specific purpose
     * of using the HashSet collection.
     * Computes the hashCode for the object based on the hashCode
     * of the properties. There is very rare chance of hash collision
     * but hopping, will not happen in the regular operations.
     * If not this solution, the other possible solution is going to
     * be in the order of quadrant O(n^2), so trying to avoid that.
     *
     * @valid none
     */
    public int hashCode() {
        int hash = 0;
        if (key != null) {
            hash = key.hashCode();
        }

        if (values == null) {
            return hash;
        }

        if (values != null) {
            for (String value : values) {
                hash = hash + value.hashCode();
            }
        }

        return hash;
    }
}