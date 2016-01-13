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

public class HostFinder extends TenantResourceFinder<Host> {
    protected static final String CLUSTER_COLUMN_NAME = "cluster";
    protected static final String NATIVEGUID_COLUMN_NAME = "nativeGuid";
    protected static final String HOST_UUID_COLUMN_NAME = "uuid";
    protected static final String COMPUTE_ELEMENT_COLUMN_NAME = "computeElement";
    protected static final String VCENTER_DATA_CENTER = "vcenterDataCenter";

    public HostFinder(ModelClient client) {
        super(Host.class, client);
    }

    public Iterable<Host> findByCluster(URI clusterId, boolean activeOnly) {
        List<NamedElement> hosts = findIdsByCluster(clusterId);
        return findByIds(toURIs(hosts), activeOnly);
    }

    public List<NamedElement> findIdsByCluster(URI clusterId) {
        return client.findBy(Host.class, CLUSTER_COLUMN_NAME, clusterId);
    }

    public Iterable<Host> findByVcenterDatacenter(URI vCenterDatacenterId) {
        List<NamedElement> hostIds = client.findBy(Host.class, VCENTER_DATA_CENTER, vCenterDatacenterId);
        return findByIds(toURIs(hostIds), true);
    }

    public Iterable<Host> findByNativeGuid(String nativeGuid, boolean activeOnly) {
        List<NamedElement> hostIds = client.findByAlternateId(Host.class, NATIVEGUID_COLUMN_NAME, nativeGuid);
        return findByIds(toURIs(hostIds), activeOnly);
    }

    public Host findByUuid(String uuid) {
        List<NamedElement> hostIds = client.findByAlternateId(Host.class, HOST_UUID_COLUMN_NAME, uuid);
        Iterable<Host> hosts = client.findByIds(Host.class, toURIs(hostIds), true);
        for (Host host : hosts) {
            return host;
        }
        return null;
    }
}
