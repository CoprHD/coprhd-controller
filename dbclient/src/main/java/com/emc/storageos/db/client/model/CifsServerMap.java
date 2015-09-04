/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

/**
 * Map of NasCifsServer (used in NasServer)
 */
public class CifsServerMap extends AbstractChangeTrackingMap<NasCifsServer> {
    @Override
    public NasCifsServer valFromByte(byte[] value) {
        NasCifsServer cifs = new NasCifsServer();
        cifs.loadBytes(value);
        return cifs;
    }

    @Override
    public byte[] valToByte(NasCifsServer value) {
        return value.toBytes();
    }
}
