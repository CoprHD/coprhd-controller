/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

/**
 * Map of Zones to Tenant Settings (used by NamespaceInfo)
 */
public class NsTenantZoneMap extends AbstractChangeTrackingMap<NamespaceZone> {
    @Override
    public NamespaceZone valFromByte(byte[] value) {
        NamespaceZone namespaceZone = new NamespaceZone();
        namespaceZone.loadBytes(value);
        return namespaceZone;
    }

    @Override
    public byte[] valToByte(NamespaceZone value) {
        return value.toBytes();
    }
}
