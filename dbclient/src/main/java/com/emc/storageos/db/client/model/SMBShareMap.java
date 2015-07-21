/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;


/**
 * Map of SMB Shares (used in FileObject)
 */
public class SMBShareMap extends AbstractChangeTrackingMap<SMBFileShare> {
    @Override
    public SMBFileShare valFromByte(byte[] value) {
        SMBFileShare smbShare = new SMBFileShare();
        smbShare.loadBytes(value);
        return smbShare;
    }

    @Override
    public byte[] valToByte(SMBFileShare value) {
        return value.toBytes();
    }
}

