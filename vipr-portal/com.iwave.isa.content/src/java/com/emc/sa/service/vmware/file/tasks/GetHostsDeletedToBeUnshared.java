/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.file.tasks;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.collections.CollectionUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.google.common.collect.Maps;
import com.iwave.ext.vmware.VCenterAPI;
import com.iwave.ext.vmware.VMwareUtils;
import com.vmware.vim25.mo.ClusterComputeResource;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostSystem;

/**
 * @author sanjes
 *
 */
public class GetHostsDeletedToBeUnshared extends ViPRExecutionTask<List<HostSystem>> {
    @Inject
    private VCenterAPI vcenter;
    private ClusterComputeResource cluster;
    private Datastore datastore;

    public GetHostsDeletedToBeUnshared(ClusterComputeResource cluster, Datastore datastore) {
        this.cluster = cluster;
        this.datastore = datastore;
        provideDetailArgs(cluster.getName(), datastore.getName());
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<HostSystem> executeTask() throws Exception {
        Map<String, HostSystem> expectedHosts = Maps.newHashMap();
        for (HostSystem clusterHost : cluster.getHosts()) {
            expectedHosts.put(VMwareUtils.getPath(clusterHost), clusterHost);
        }
        Map<String, HostSystem> actualHosts = Maps.newHashMap();
        for (HostSystem datastoreHost : VMwareUtils.getHostsForDatastore(vcenter, datastore)) {
            actualHosts.put(VMwareUtils.getPath(datastoreHost), datastoreHost);
        }
        List<HostSystem> hostsToBeDeleted = (List<HostSystem>) CollectionUtils.subtract(actualHosts.keySet(), expectedHosts.keySet());
        if (CollectionUtils.isEmpty(hostsToBeDeleted)) {
            ExecutionUtils.fail("failTask.getHostsAddedToBeUnshared.noHostsFoundToBeUnshared", datastore.getName());
        }
        return hostsToBeDeleted;

    }
}
