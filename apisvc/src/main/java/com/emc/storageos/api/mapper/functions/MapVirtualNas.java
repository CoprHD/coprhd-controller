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

public class MapVirtualNas implements Function<VirtualNAS, VirtualNASRestRep> {
    public static final MapVirtualNas instance = new MapVirtualNas();

    // The DB client is required to query the FCEndpoint
    private DbClient dbClient;

    public static MapVirtualNas getInstance(DbClient dbClient) {
        instance.setDbClient(dbClient);
        return instance;
    }

    private MapVirtualNas() {
    }

    private void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public VirtualNASRestRep apply(VirtualNAS resource) {
    	VirtualNASRestRep vNasRestRep = SystemsMapper.map(resource);
        return vNasRestRep;
    }

    /**
     * Translate <code>VirtualNAS</code> object to <code>VirtualNASRestRep</code>
     * 
     * @param storagePort
     * @return
     */
    public VirtualNASRestRep toVirtualNasRestRep(VirtualNAS vNas) {
        return apply(vNas);
    }

    
    
}
