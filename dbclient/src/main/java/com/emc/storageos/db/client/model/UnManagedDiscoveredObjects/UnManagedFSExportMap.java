/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.UnManagedDiscoveredObjects;

import com.emc.storageos.db.client.model.AbstractChangeTrackingMap;

public class UnManagedFSExportMap extends AbstractChangeTrackingMap<UnManagedFSExport> {

    @Override
    public UnManagedFSExport valFromByte(byte[] value) {
        UnManagedFSExport unManagedFileExport = new UnManagedFSExport();
        unManagedFileExport.loadBytes(value);
        return unManagedFileExport;
    }

    @Override
    public byte[] valToByte(UnManagedFSExport value) {
        return value.toBytes();
    }

}
