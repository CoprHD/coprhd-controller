/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.file.tasks;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.commons.collections.CollectionUtils;

import com.beust.jcommander.internal.Lists;
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
public class GetHostsAddedToBeShared extends ViPRExecutionTask<List<HostSystem>> {
    @Inject
    private VCenterAPI vcenter;
    private ClusterComputeResource cluster;
    private Datastore datastore;

    public GetHostsAddedToBeShared(ClusterComputeResource cluster, Datastore datastore) {
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
        Set<String> hostsNameAdded = (Set<String>) CollectionUtils.subtract(expectedHosts.keySet(), actualHosts.keySet());
        List<HostSystem> hostAdded = Lists.newArrayList();
        if (CollectionUtils.isEmpty(hostsNameAdded)) {
            ExecutionUtils.fail("failTask.getHostsAddedToBeShared.noHostsFoundToBeShared", datastore.getName());
        } else {
            for (String host : hostsNameAdded) {
                hostAdded.add(expectedHosts.get(host));
            }
        }
        return hostAdded;

    }

}
