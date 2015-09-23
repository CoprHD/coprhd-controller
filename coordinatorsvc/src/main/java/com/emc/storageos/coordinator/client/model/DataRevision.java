/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.model;

import java.util.Map;

import com.emc.storageos.coordinator.client.service.PropertyInfoUtil;
import com.emc.storageos.coordinator.exceptions.DecodingException;

/**
 * DataRevision represents a data revision number.
 */
public class DataRevision implements CoordinatorSerializable {
    private static final String TARGET_PROPERTY_ID = "global";
    private static final String TARGET_PROPERTY_ATTR = "datarevision";
    
    private static final String KEY_DATA_REVISION = "target_data_revision";
    
    private String targetRevision;
    
    public DataRevision() {
    }
    
    public DataRevision(String revision) {
        targetRevision = revision;
    }
    
    public String getTargetRevision() {
        return targetRevision;
    }
    
    @Override
    public String encodeAsString() {
        final StringBuilder s = new StringBuilder();
        s.append(KEY_DATA_REVISION);
        s.append(PropertyInfoExt.ENCODING_EQUAL);
        s.append(targetRevision);
        return s.toString();
    }

    @SuppressWarnings("unchecked")
    @Override
    public DataRevision decodeFromString(String infoStr) throws DecodingException {
        final String[] strings = new String[]{infoStr};
        Map<String, String> props = PropertyInfoUtil.splitKeyValue(strings);
        return new DataRevision(props.get(KEY_DATA_REVISION));
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(TARGET_PROPERTY_ID, Constants.TARGET_DATA_REVISION_PROPERTY, TARGET_PROPERTY_ATTR);
    }

    @Override
    public String toString() {
        final StringBuilder s = new StringBuilder();
        s.append(targetRevision == null ? "- default -" : targetRevision);
        return s.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (targetRevision != null && obj != null && obj instanceof DataRevision) {
            return targetRevision.equals(((DataRevision)obj).getTargetRevision());
        }
        return false;
    }
}
