/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.dao;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.ModelClient;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.ControlStation;
import com.emc.storageos.db.client.model.Host;

public class HMCControlStatiionFinder extends HostFinder {
    private static final String CONTROL_STATION_COLUMN_NAME = "controlstation";

    public HMCControlStatiionFinder(ModelClient client) {
        super(client);
    }

    public Iterable<Host> findByDatacenter(URI ccId, boolean activeOnly) {
        List<NamedElement> cs = findIdsByDatacenter(ccId);
        return findByIds(toURIs(cs), activeOnly);
    }

    public List<NamedElement> findIdsByDatacenter(URI datacenterId) {
        return client.findBy(ControlStation.class, CONTROL_STATION_COLUMN_NAME, datacenterId);
    }
}
