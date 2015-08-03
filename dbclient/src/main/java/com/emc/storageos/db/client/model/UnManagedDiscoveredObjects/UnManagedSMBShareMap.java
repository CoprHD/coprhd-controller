/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.UnManagedDiscoveredObjects;

import com.emc.storageos.db.client.model.AbstractChangeTrackingMap;



/**
 * Map of SMB Shares (used in FileObject)
 */
public class UnManagedSMBShareMap extends AbstractChangeTrackingMap<UnManagedSMBFileShare> {
    @Override
    public UnManagedSMBFileShare valFromByte(byte[] value) {
        UnManagedSMBFileShare unManagedSmbShare = new UnManagedSMBFileShare();
        unManagedSmbShare.loadBytes(value);
        return unManagedSmbShare;
    }

    @Override
    public byte[] valToByte(UnManagedSMBFileShare value) {
        return value.toBytes();
    }
}
