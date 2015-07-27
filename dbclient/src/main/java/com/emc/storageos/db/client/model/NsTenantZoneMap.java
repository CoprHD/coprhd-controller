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
