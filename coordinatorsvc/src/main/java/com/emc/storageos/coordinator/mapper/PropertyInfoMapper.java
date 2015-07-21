/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.mapper;

import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.service.PropertyInfoUtil;
import com.emc.storageos.model.property.PropertyInfo;

public class PropertyInfoMapper {
    
    /**
     * Method used to construct a property object from property string
     *
     * @param stateStr      Property string
     * @return              Property object decoded from string
     * @throws Exception
     */
   public static PropertyInfo decodeFromString(String stateStr) throws Exception {
        if (stateStr != null) {
            final String[] strings = stateStr.split(PropertyInfoExt.ENCODING_SEPARATOR);
            if (strings.length == 0) {
                return new PropertyInfo();
            }

            return new PropertyInfo(PropertyInfoUtil.splitKeyValue(strings));
        }
        return null;
    }
}
