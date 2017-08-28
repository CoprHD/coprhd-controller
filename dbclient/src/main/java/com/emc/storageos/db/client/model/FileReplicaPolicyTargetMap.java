/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

public class FileReplicaPolicyTargetMap extends AbstractChangeTrackingMap<FileReplicaPolicyTarget> {
    @Override
    public FileReplicaPolicyTarget valFromByte(byte[] value) {
        FileReplicaPolicyTarget fileReplicaPolicyTarget = new FileReplicaPolicyTarget();
        fileReplicaPolicyTarget.loadBytes(value);
        return fileReplicaPolicyTarget;
    }

    @Override
    public byte[] valToByte(FileReplicaPolicyTarget value) {
        return value.toBytes();
    }
}
