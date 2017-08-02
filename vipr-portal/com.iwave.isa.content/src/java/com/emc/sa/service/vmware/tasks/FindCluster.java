/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.tasks;

import javax.inject.Inject;

import com.emc.sa.engine.ExecutionTask;
import com.iwave.ext.vmware.VCenterAPI;
import com.iwave.ext.vmware.VMwareUtils;
import com.vmware.vim25.HostSystemConnectionState;
import com.vmware.vim25.mo.ClusterComputeResource;
import com.vmware.vim25.mo.HostSystem;

public class FindCluster extends ExecutionTask<ClusterComputeResource> {
    @Inject
    private VCenterAPI vcenter;
    private final String datacenterName;
    private final String clusterName;
    private boolean checkClusterConnectivity;

    public FindCluster(String datacenterName, String clusterName, boolean checkClusterConnectivity) {
        this.datacenterName = datacenterName;
        this.clusterName = clusterName;
        this.checkClusterConnectivity = checkClusterConnectivity;
        provideDetailArgs(clusterName, datacenterName);
    }

    @Override
    public ClusterComputeResource executeTask() throws Exception {
        debug("Executing: %s", getDetail());
        ClusterComputeResource cluster = vcenter.findCluster(datacenterName, clusterName);
        if (cluster == null) {
            throw stateException("FindCluster.illegalState.noClusterInDataCenter", datacenterName, clusterName);
        }

        // Check cluster hosts connection state
        HostSystem[] hosts = cluster.getHosts();
        if (hosts == null) {
            throw stateException("FindCluster.illegalState.unableToListHost", clusterName);
        }

        if (checkClusterConnectivity) {
            for (HostSystem host : hosts) {
                checkConnectionState(host);
            }
        }

        return cluster;
    }

    private void checkConnectionState(HostSystem host) {
        // Check the connection state of this host
        HostSystemConnectionState connectionState = VMwareUtils.getConnectionState(host);
        logInfo("find.cluster.host.state", host.getName(), connectionState);
        if (connectionState == null) {
            throw stateException("FindCluster.illegalState.noHostState", host.getName(), datacenterName);
        } else if (connectionState == HostSystemConnectionState.notResponding) {
            throw stateException("FindCluster.illegalState.notResponding", host.getName());
        } else if (connectionState == HostSystemConnectionState.disconnected) {
            throw stateException("FindCluster.illegalState.notConnected", host.getName());
        }
    }
}
