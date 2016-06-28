/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block.tasks;

import java.util.Collection;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionTask;
import com.google.common.collect.Maps;
import com.iwave.ext.vmware.VCenterAPI;
import com.iwave.ext.vmware.VMwareUtils;
import com.vmware.vim25.mo.ClusterComputeResource;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostSystem;

public class VerifyDatastoreHostMounts extends ExecutionTask<Void> {
    @Inject
    private VCenterAPI vcenter;
    private HostSystem host;
    private ClusterComputeResource cluster;
    private Datastore datastore;

    public VerifyDatastoreHostMounts(HostSystem host, ClusterComputeResource cluster, Datastore datastore) {
        this.host = host;
        this.cluster = cluster;
        this.datastore = datastore;
    }

    @Override
    public void execute() throws Exception {
        Map<String, HostSystem> expectedHosts = Maps.newHashMap();
        if (cluster != null) {
            for (HostSystem clusterHost : cluster.getHosts()) {
                expectedHosts.put(VMwareUtils.getPath(clusterHost), clusterHost);
            }
        }
        else {
            expectedHosts.put(VMwareUtils.getPath(host), host);
        }

        Map<String, HostSystem> actualHosts = Maps.newHashMap();
        for (HostSystem datastoreHost : VMwareUtils.getHostsForDatastore(vcenter, datastore)) {
            actualHosts.put(VMwareUtils.getPath(datastoreHost), datastoreHost);
        }

        Collection<?> added = CollectionUtils.subtract(actualHosts.keySet(), expectedHosts.keySet());
        Collection<?> removed = CollectionUtils.subtract(expectedHosts.keySet(), actualHosts.keySet());
        if (!(added.isEmpty() && removed.isEmpty())) {
            String expected = StringUtils.join(expectedHosts.keySet(), ", ");
            String actual = StringUtils.join(actualHosts.keySet(), ", ");
            throw stateException("illegalState.VerifyDatastoreHostMounts", expected, actual);
        }
    }
}
