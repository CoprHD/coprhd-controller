/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.dao;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.ModelClient;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.Host;

public class ESXHostFinder extends HostFinder {
    private static final String DATACENTER_COLUMN_NAME = "vcenterDataCenter";
    
    public ESXHostFinder(ModelClient client) {
        super(client);
    }

    public Iterable<Host> findByDatacenter(URI datacenterId, boolean activeOnly) {
        List<NamedElement> esxHosts = findIdsByDatacenter(datacenterId);
        return findByIds(toURIs(esxHosts), activeOnly);
    }
    
    public List<NamedElement> findIdsByDatacenter(URI datacenterId) {
        return client.findBy(Host.class, DATACENTER_COLUMN_NAME, datacenterId);
    }
}
