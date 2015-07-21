/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * AttributeMapBuilder is design based on the builder pattern
 * to construct a map based on the CoS, CoSCreateParam and Provisioning
 * attributes. 
 * 
 * Steps for MapCreator to construct a map object with CoS attributes:
 * 1. create a respective builder object by passing required object.
 * 2. Now call buildMap using builder.
 * 3. buildMap will return a map with all populated attributes. 
 */
public abstract class AttributeMapBuilder {
    /**
     * AttributeMap to be constructed by builder.
     */
    protected Map<String, Object> _attributeMap = new HashMap<String, Object>();

    /**
     * gives the responsibility to builder subclasses to populate respective builder
     * attributes to build attributeMap.
     */
    public abstract Map<String, Object> buildMap();
    
    /**
     * Set the attributes in AttributeMap.
     * @param attributeMap
     * @param attributename
     * @param attributeValue
     */
    public void putAttributeInMap(String attributeName, Object attributeValue) {
        if (null != attributeValue && null != attributeName) {
            _attributeMap.put(attributeName, attributeValue);
        }
    }
}
