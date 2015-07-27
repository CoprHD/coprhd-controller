/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

/**
 * Map of FSExport (used in FileShare)
 */
public class FSExportMap extends AbstractChangeTrackingMap<FileExport> {
    @Override
    public FileExport valFromByte(byte[] value) {
        FileExport fileExport = new FileExport();
        fileExport.loadBytes(value);
        return fileExport;
    }

    @Override
    public byte[] valToByte(FileExport value) {
        return value.toBytes();
    }
}
