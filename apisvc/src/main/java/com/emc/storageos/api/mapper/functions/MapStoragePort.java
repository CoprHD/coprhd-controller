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
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.google.common.base.Function;

public class MapStoragePort implements Function<StoragePort, StoragePortRestRep> {
    public static final MapStoragePort instance = new MapStoragePort();

    // The DB client is required to query the FCEndpoint
    private DbClient dbClient;

    public static MapStoragePort getInstance(DbClient dbClient) {
        instance.setDbClient(dbClient);
        return instance;
    }

    private MapStoragePort() {
    }

    private void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public StoragePortRestRep apply(StoragePort resource) {
        StoragePortRestRep storagePortRestRep = SystemsMapper.map(resource);
        applyAliasToStoragePortRestRep(storagePortRestRep);
        applyAdapterNameToStoragePortRestRep(storagePortRestRep, resource);
        return storagePortRestRep;
    }

    /**
     * Translate <code>StoragePort</code> object to <code>StoragePortRestRep</code>
     * 
     * @param storagePort
     * @return
     */
    public StoragePortRestRep toStoragePortRestRep(StoragePort storagePort) {
        return apply(storagePort);
    }

    /**
     * Convenient method to map wwn alias to its corresponded port wwn id
     * 
     * @param storagePortRestRep
     */
    private void applyAliasToStoragePortRestRep(StoragePortRestRep storagePortRestRep) {
        if (dbClient == null) {
            return;
        }

        URIQueryResultList uriList = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                getFCEndpointRemotePortNameConstraint(storagePortRestRep.getPortNetworkId()), uriList);
        for (URI uri : uriList) {
            FCEndpoint ep = dbClient.queryObject(FCEndpoint.class, uri);
            if (ep != null && !StringUtils.isEmpty(ep.getRemotePortAlias())) {
                storagePortRestRep.setPortAlias(ep.getRemotePortAlias());
            }
        }
    }

    /**
     * Adds StorageHADomain name into StoragePortRestRep object,
     * if the StoragePort's HADomain is not set, then StoragePort's
     * PortGroup value will be assigned to StoragePortRestRep
     * adapterName attribute
     * StoragePort is not set for Isilon and DataDomain devices.
     * 
     * @param storagePortRestRep
     * @param resource
     */

    private void applyAdapterNameToStoragePortRestRep(StoragePortRestRep storagePortRestRep, StoragePort resource) {
        if (dbClient == null) {
            return;
        }
        if (resource != null && resource.getStorageHADomain() != null) {
            StorageHADomain tempObj = dbClient.queryObject(
                    StorageHADomain.class, resource.getStorageHADomain());
            if (tempObj.getAdapterName() != null
                    && !StringUtils.isEmpty(tempObj.getAdapterName())) {
                storagePortRestRep.setAdapterName(tempObj.getAdapterName());
            } else {
                storagePortRestRep.setAdapterName(resource.getPortGroup());
            }
        } else if (resource.getStorageHADomain() == null) {
            /**
             * When StoragePort's StorageHADomain value is not set
             * assign StoragePort's PortGroup value to adapter-name
             */
            storagePortRestRep.setAdapterName(resource.getPortGroup());
        }
    }
}
