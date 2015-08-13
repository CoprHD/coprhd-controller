/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import java.net.URI;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.api.mapper.SystemsMapper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.FCEndpoint;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.vnas.VirtualNASRestRep;
import com.google.common.base.Function;

public class MapVirtualNAS implements Function<VirtualNAS, VirtualNASRestRep> {
    public static final MapVirtualNAS instance = new MapVirtualNAS();

    // The DB client is required to query the FCEndpoint
    private DbClient dbClient;

    public static MapVirtualNAS getInstance(DbClient dbClient) {
        instance.setDbClient(dbClient);
        return instance;
    }

    private MapVirtualNAS() {
    }

    private void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }


    /**
     * Translate <code>StoragePort</code> object to <code>StoragePortRestRep</code>
     * 
     * @param storagePort
     * @return
     */
    public VirtualNASRestRep toStoragePortRestRep(VirtualNAS virtualNas) {
        return apply(virtualNas);
    }

    
    @Override
    public VirtualNASRestRep apply(VirtualNAS input) {
        
        VirtualNASRestRep virtualNASRestRep = SystemsMapper.map(input);
        return virtualNASRestRep;
    }
}
