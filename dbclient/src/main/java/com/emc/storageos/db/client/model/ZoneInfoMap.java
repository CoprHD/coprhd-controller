/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

/**
 * Map of ZoneInfo used in UnManagedExportMask to store the zoning 
 * paths between the initiators and ports of the UnManagedExportMask
 */
public class ZoneInfoMap extends AbstractChangeTrackingMap<ZoneInfo> {
    @Override
    public ZoneInfo valFromByte(byte[] value) {
        ZoneInfo zoneInfo = new ZoneInfo();
        zoneInfo.loadBytes(value);
        return zoneInfo;
    }

    @Override
    public byte[] valToByte(ZoneInfo value) {
        return value.toBytes();
    }
}
