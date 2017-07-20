/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import java.util.List;
import java.net.URI;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.mapper.SystemsMapper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StoragePortGroup;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.model.portgroup.StoragePortGroupRestRep;
import com.google.common.base.Function;

public class MapStoragePortGroup implements Function<StoragePortGroup, StoragePortGroupRestRep> {
    public static final MapStoragePortGroup instance = new MapStoragePortGroup();

    // The DB client is required to query the storage ports
    private DbClient dbClient;

    public static MapStoragePortGroup getInstance(DbClient dbClient) {
        instance.setDbClient(dbClient);
        return instance;
    }

    private MapStoragePortGroup() {
    }

    private void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public StoragePortGroupRestRep apply(StoragePortGroup resource) {
        StoragePortGroupRestRep storagePortGroupRestRep = SystemsMapper.map(resource);
        applyPortsToStoragePortGroupRestRep(storagePortGroupRestRep, resource);

        return storagePortGroupRestRep;
    }

    /**
     * Translate StoragePortGroup object to StoragePortGroupRestRep
     * 
     * @param storagePortGroup StoragePortGroup instance
     * @return StoragePortGroupRestRep
     */
    public StoragePortGroupRestRep toStoragePortGroupRestRep(StoragePortGroup storagePortGroup) {
        return apply(storagePortGroup);
    }

    /**
     * Update the storagePortGrouprestRep instance with the storage ports information
     * 
     * @param storagePortGroupRep The StoragePortGroupRestRep instance to be updated
     * @param portGroup The StoragePortGroup instance
     */
    private void applyPortsToStoragePortGroupRestRep(StoragePortGroupRestRep storagePortGroupRep,
            StoragePortGroup portGroup) {
        StringSet ports = portGroup.getStoragePorts();
        if (ports == null || ports.isEmpty()) {
            return;
        }
        List<URI> portUris = StringSetUtil.stringSetToUriList(ports);
        for (URI portUri : portUris) {
            StoragePort port = dbClient.queryObject(StoragePort.class, portUri);
            storagePortGroupRep.getStoragePorts().getPorts().add(DbObjectMapper.toNamedRelatedResource(port, port.getPortName()));
        }
    }

}
