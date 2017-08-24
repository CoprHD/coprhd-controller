/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware;

import static com.emc.sa.service.ServiceParams.DATACENTER;
import static com.emc.sa.service.ServiceParams.HOST;
import static com.emc.sa.service.ServiceParams.VCENTER;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.vmware.vim25.mo.ClusterComputeResource;
import com.vmware.vim25.mo.HostSystem;

public abstract class VMwareHostService extends ViPRService {
    @Param(VCENTER)
    protected URI vcenterId;
    @Param(DATACENTER)
    protected URI datacenterId;
    @Param(HOST)
    protected URI hostId;

    protected VMwareSupport vmware = new VMwareSupport();
    protected VcenterDataCenter datacenter;
    protected Host esxHost;
    protected Cluster hostCluster;
    protected HostSystem host;
    protected ClusterComputeResource cluster;

    public boolean checkClusterConnectivity() {
        return true;
    }

    private void initHost() {
        datacenter = vmware.getDatacenter(datacenterId);

        if (BlockStorageUtils.isHost(hostId)) {
            esxHost = getModelClient().hosts().findById(hostId);

            if (esxHost == null) {
                throw new IllegalArgumentException("Host " + hostId + " not found");
            }

            logInfo("vmware.service.target.host", esxHost.getLabel());

        } else {
            hostCluster = getModelClient().clusters().findById(hostId);
            if (hostCluster == null) {
                throw new IllegalArgumentException("Cluster " + hostId + " not found");
            }

            List<Host> hosts = getModelClient().hosts().findByCluster(hostId);
            if (hosts.isEmpty()) {
                throw new IllegalArgumentException("Cluster '" + hostCluster.getLabel() + "' [" + hostId
                        + "] contains no hosts");
            }

            cluster = vmware.getCluster(datacenter.getLabel(), hostCluster.getLabel(), checkClusterConnectivity());

            esxHost = getConnectedHost(hosts, datacenter);

            if (esxHost == null) {
                throw new IllegalArgumentException("Cluster '" + hostCluster.getLabel() + "' [" + hostId
                        + "] does not contain any connected hosts");
            }

            logInfo("vmware.service.target.cluster", hostCluster.getLabel(), hosts.size());

        }
        host = vmware.getHostSystem(datacenter.getLabel(), esxHost.getLabel(), true);

    }

    /**
     * Get the first connected host from the given set of hosts
     * 
     * @param hosts list of hosts
     * @param datacenter the datacenter the hosts belong to
     * @return connected host or null if no hosts are connected
     */
    protected Host getConnectedHost(List<Host> hosts, VcenterDataCenter datacenter) {
        for (Host host : hosts) {
            HostSystem esxHost = null;
            esxHost = vmware.getHostSystem(datacenter.getLabel(), host.getLabel(), false);
            if (VMwareSupport.isHostConnected(esxHost)) {
                return host;
            }
        }
        return null;
    }

    protected void connectAndInitializeHost() {
        vmware.connect(vcenterId);
        initHost();
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        connectAndInitializeHost();
        validateClusterHosts();
    }

    @Override
    public void destroy() {
        super.destroy();
        vmware.disconnect();
    }

    protected void acquireHostLock() {
        acquireHostLock(esxHost, hostCluster);
    }

    /**
     * Validates the vCenter cluster hosts match the same hosts we have in our database for the cluster. If there is a mismatch the check
     * will fail the order.
     */
    protected void validateClusterHosts() {
        if (hostCluster != null) {
            VcenterDataCenter datacenter = getModelClient().datacenters().findById(datacenterId);
            Cluster cluster = getModelClient().clusters().findById(hostCluster.getId());

            ClusterComputeResource vcenterCluster = vmware.getCluster(datacenter.getLabel(), cluster.getLabel(),
                    checkClusterConnectivity());

            if (vcenterCluster == null) {
                ExecutionUtils.fail("failTask.vmware.cluster.notfound", args(), args(cluster.getLabel()));
            }

            Map<String, String> vCenterHostUuids = Maps.newHashMap();
            for (HostSystem hostSystem : vcenterCluster.getHosts()) {
                if (hostSystem.getHardware() != null && hostSystem.getHardware().systemInfo != null) {
                    vCenterHostUuids.put(hostSystem.getHardware().systemInfo.uuid, hostSystem.getName());
                }
            }

            List<Host> dbHosts = getModelClient().hosts().findByCluster(hostCluster.getId());
            Map<String, String> dbHostUuids = Maps.newHashMap();
            for (Host host : dbHosts) {
                // Validate the hosts within the cluster all have good discovery status
                if (!DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.toString().equalsIgnoreCase(host.getCompatibilityStatus())) {
                    ExecutionUtils.fail("failTask.vmware.cluster.hostincompatible", args(), args(cluster.getLabel(), host.getLabel()));
                } else if (DiscoveredDataObject.DataCollectionJobStatus.ERROR.toString().equalsIgnoreCase(host.getDiscoveryStatus())) {
                    ExecutionUtils.fail("failTask.vmware.cluster.hostsdiscoveryfailed", args(), args(cluster.getLabel(), host.getLabel()));
                }

                dbHostUuids.put(host.getUuid(), host.getLabel());

            }

            if (!vCenterHostUuids.keySet().equals(dbHostUuids.keySet())) {
                MapDifference<String, String> differences = Maps.difference(vCenterHostUuids, dbHostUuids);
                ExecutionUtils.fail("failTask.vmware.cluster.mismatch", args(), cluster.getLabel(),
                        differences.entriesOnlyOnLeft().values(), differences.entriesOnlyOnRight().values());
            } else {
                info("Hosts in cluster %s matches correctly", cluster.getLabel());
            }

        }
    }
}
