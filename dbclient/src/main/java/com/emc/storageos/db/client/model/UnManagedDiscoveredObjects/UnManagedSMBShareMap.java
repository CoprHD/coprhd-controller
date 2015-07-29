/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.UnManagedDiscoveredObjects;

import com.emc.storageos.db.client.model.AbstractChangeTrackingMap;

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
