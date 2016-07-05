/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.dao;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.ModelClient;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.ManagementStation;

public class HMCManagementStatiionFinder extends HostFinder {
    private static final String MANAGEMENT_STATION_COLUMN_NAME = "controlstation";

    public HMCManagementStatiionFinder(ModelClient client) {
        super(client);
    }

    public Iterable<Host> findByDatacenter(URI ccId, boolean activeOnly) {
        List<NamedElement> cs = findIdsByDatacenter(ccId);
        return findByIds(toURIs(cs), activeOnly);
    }

    public List<NamedElement> findIdsByDatacenter(URI datacenterId) {
        return client.findBy(ManagementStation.class, MANAGEMENT_STATION_COLUMN_NAME, datacenterId);
    }
}
