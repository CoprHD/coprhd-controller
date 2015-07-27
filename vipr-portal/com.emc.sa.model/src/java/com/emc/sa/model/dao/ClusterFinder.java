/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.Cluster;

public class ClusterFinder extends TenantModelFinder<Cluster> {
    protected static final String DATACENTER_COLUMN_NAME = "vcenterDataCenter";
    protected static final String PROJECT_COLUMN_NAME = "project";

    public ClusterFinder(DBClientWrapper client) {
        super(Cluster.class, client);
    }

    public List<Cluster> findByDatacenter(URI datacenterId) {
        List<NamedElement> clusters = findIdsByDatacenter(datacenterId);
        return findByIds(toURIs(clusters));
    }

    public List<NamedElement> findIdsByDatacenter(URI datacenterId) {
        return client.findBy(Cluster.class, DATACENTER_COLUMN_NAME, datacenterId);
    }

    public List<Cluster> findByProject(URI projectId) {
        List<NamedElement> clusters = findIdsByProject(projectId);
        return findByIds(toURIs(clusters));
    }

    public List<NamedElement> findIdsByProject(URI projectId) {
        return client.findBy(Cluster.class, PROJECT_COLUMN_NAME, projectId);
    }
}
