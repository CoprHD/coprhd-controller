/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.Host;

public class HostFinder extends TenantResourceFinder<Host> {
    protected static final String CLUSTER_COLUMN_NAME = "cluster";

    public HostFinder(DBClientWrapper client) {
        super(Host.class, client);
    }

    public List<Host> findByCluster(URI clusterId) {
        List<NamedElement> hosts = findIdsByCluster(clusterId);
        return findByIds(toURIs(hosts));
    }

    public List<NamedElement> findIdsByCluster(URI clusterId) {
        return client.findBy(Host.class, CLUSTER_COLUMN_NAME, clusterId);
    }

}
