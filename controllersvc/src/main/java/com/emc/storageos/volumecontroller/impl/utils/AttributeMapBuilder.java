/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2013. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
     * 
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
