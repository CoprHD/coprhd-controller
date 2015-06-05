/**
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

import java.util.Map;

import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.volumecontroller.AttributeMatcher.Attributes;

/**
 * AttributeMapBuilder to construct a map using the attributes specified in
 * provisioning operation.
 *
 */
public class ProvisioningAttributeMapBuilder extends AttributeMapBuilder {
    /**
     * Holds size attribute.    
     */
    private long _size;
    /**
     * Holds neighborhoodId.
     */
    private String _neighborhoodId;
    /**
     * Holds size attribute.    
     */
    private long _thinVolumePreAllocationSize;
    /**
     * Constructor to initialize with provisioning attributes.
     * @param size
     * @param protocols
     * @param neighborhoodId
     * @param thinVolumePreAllocationSize
     */
    public ProvisioningAttributeMapBuilder(long size, String neighborhoodId, long thinVolumePreAllocationSize) {
        _size = size;
        _neighborhoodId = neighborhoodId;
        _thinVolumePreAllocationSize = thinVolumePreAllocationSize;
    }

    @Override
    public Map<String, Object> buildMap() {
        putAttributeInMap(Attributes.size.toString(), _size);
        StringSet neighborhoodSet = new StringSet();
        neighborhoodSet.add(_neighborhoodId);
        putAttributeInMap(Attributes.varrays.toString(), neighborhoodSet);
        putAttributeInMap(Attributes.thin_volume_preallocation_size.toString(), _thinVolumePreAllocationSize);
        return _attributeMap;
    }
}
