/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
