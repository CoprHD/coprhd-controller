/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute;

import static com.emc.sa.service.ServiceParams.CLUSTER;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.host.HostRestRep;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

@Service("RemoveHostFromCluster")
public class RemoveHostFromClusterService extends ViPRService {

    @Param(CLUSTER)
    protected URI clusterId;

    @Param(ServiceParams.HOST)
    protected List<String> ids;

    private Cluster cluster;

    private List<URI> hostIds;
    private Map<URI, String> hostURIMap = new HashMap<URI,String>();

    @Override
    public void precheck() throws Exception {
        StringBuilder preCheckErrors = new StringBuilder();
        hostIds = uris(ids);
        cluster = BlockStorageUtils.getCluster(clusterId);
        if (cluster == null) {
            preCheckErrors.append("Cluster doesn't exist for ID " + clusterId + " ");
        }
        List<String> nonVblockhosts = new ArrayList<>();
        for (URI hostId : hostIds) {
            Host host = BlockStorageUtils.getHost(hostId);
            if (host == null) {
                preCheckErrors.append("Host doesn't exist for ID " + hostId);
            } else if (!host.getCluster().equals(clusterId)) {
                preCheckErrors.append("Host " + host.getLabel() + " is not associated with cluster: " + cluster.getLabel() + " ");
            } else if (NullColumnValueGetter.isNullURI(host.getComputeElement()) && NullColumnValueGetter.isNullURI(host.getServiceProfile())) {
                nonVblockhosts.add(host.getLabel());
            }
            hostURIMap.put(hostId, host.getLabel());
        }
        // If a non-vblock host is being decommissioned, fail the order. Only vblock hosts can be decommissioned.
        if (!CollectionUtils.isEmpty(nonVblockhosts)) {
            logError("computeutils.deactivatecluster.deactivate.nonvblockhosts", nonVblockhosts);
            preCheckErrors.append("Cannot decommission the following non-vBlock hosts - ");
            preCheckErrors.append(nonVblockhosts);
            preCheckErrors.append(".  Non-vblock hosts cannot be decommissioned from VCE Vblock catalog services. ");
        }

        preCheckErrors = ComputeUtils.verifyClusterInVcenter(cluster, preCheckErrors);

        // Validate all of the boot volumes are still valid.
        if (!validateBootVolumes(hostURIMap)) {
            logError("computeutils.deactivatecluster.deactivate.bootvolumes", cluster.getLabel());
            preCheckErrors.append("Cluster ").append(cluster.getLabel())
            .append(" has different boot volumes than what controller provisioned.  Cannot delete original boot volume in case it was re-purposed. ");
        }

        // Verify the hosts are still part of the cluster we have reported for it on ESX.
        if (!ComputeUtils.verifyHostInVcenterCluster(cluster, hostIds)) {
            logError("computeutils.deactivatecluster.deactivate.hostmovedcluster", cluster.getLabel(),
                    Joiner.on(',').join(hostURIMap.values()));
            preCheckErrors.append("Cluster ").append(cluster.getLabel())
            .append(" no longer contains one or more of the hosts requesting decommission.  Cannot decommission in current state.  Recommended " +
            "to run vCenter discovery and address actionable events before attempting decommission of hosts in this cluster. ");
        }

        if (preCheckErrors.length() > 0) {
            throw new IllegalStateException(preCheckErrors.toString() + 
                    ComputeUtils.getContextErrors(getModelClient()));
        }
    }

    /**
     * Validate the boot volume associated with the hosts we wish to remove.
     *
     * @param hostIdToNameMap
     *            map of host ID to hostname. We are only using the host ID key.
     * @return false if we can reach the host and determine the boot volume is no longer there.
     */
    private boolean validateBootVolumes(Map<URI, String> hostIdToNameMap) {
        // If the cluster isn't returned properly, not found in DB, do not delete the boot volume until
        // the references are fixed.
        if (cluster == null || cluster.getInactive()) {
            logError("computeutils.removebootvolumes.failure.cluster");
            return false;
        }

        // Get all of the hosts for the cluster, create a list of hosts we are interested in removing ONLY.
        List<HostRestRep> allClusterHosts = ComputeUtils.getHostsInCluster(clusterId);
        List<HostRestRep> hostsToValidate = new ArrayList<>();
        for (HostRestRep clusterHost : allClusterHosts) {
            if (hostIdToNameMap.containsKey(clusterHost.getId())) {
                hostsToValidate.add(clusterHost);
            }
        }
        return ComputeUtils.validateBootVolumes(cluster, hostsToValidate);
    }

    @Override
    public void execute() throws Exception {
        // In order to remove the hosts from cluster
        // 1. Set the cluster uri = null on the host itself - Should be taken care of during deactivate host
        // 2. Delete hosts which will also take care of removing boot volumes and dissociate any shared storage that is
        // associated with the host.
        //
        // Remove host from cluster
        // Remove hosts

        if (hostIds.isEmpty()) {
            return;
        }

        // get boot vols to be deleted (so we can check afterwards)
        List<URI> bootVolsToBeDeleted = Lists.newArrayList();
        List<Host> hostsToBeDeleted = Lists.newArrayList();
        for (URI hostURI : hostIds) {
            // VBDU TODO, COP-28448, If the boot volume is null for a host, the code goes ahead and runs export update
            // operations on all the export Groups referencing the hosts. Ideally, we should run the exports only for
            // shared export groups, right?
            Host host = BlockStorageUtils.getHost(hostURI);
            hostsToBeDeleted.add(host);
            URI bootVolURI = host.getBootVolumeId();
            if (bootVolURI != null) {
                BlockObjectRestRep bootVolRep = null;
                try{
                    bootVolRep = BlockStorageUtils.getBlockResource(bootVolURI);
                } catch(Exception e){
                    //Invalid boot volume reference. Ignore
                }
                if (bootVolRep!=null && !bootVolRep.getInactive()) {
                    // VBDU TODO: COP-28447, We're assuming the volume we're deleting is still the boot volume, but it
                    // could have been manually dd'd (migrated) to another volume and this volume could be re-purposed
                    // elsewhere. We should verify this is the boot volume on the server before attempting to
                    // delete it.Same comment in RemoveComputeClusterService.
                    bootVolsToBeDeleted.add(bootVolURI);
                }
            }
        }
        //acquire host locks before proceeding with deactivating hosts.
        for (Host host : hostsToBeDeleted) {
            acquireHostLock(host, cluster);
        }

        // removing hosts also removes associated boot volumes and exports
        List<URI> successfulHostIds = ComputeUtils.deactivateHostURIs(hostURIMap);

        // fail order if no hosts removed
        if (successfulHostIds.isEmpty()) {
            throw new IllegalStateException(ExecutionUtils.getMessage("computeutils.deactivatehost.deactivate.failure", ""));
        }

        // check all hosts were removed
        if (successfulHostIds.size() < hostIds.size()) {
            for (URI hostURI : hostIds) {
                if (!successfulHostIds.contains(hostURI)) {
                    logError("computeutils.deactivatehost.failure", hostURI, clusterId);
                }
            }
            setPartialSuccess();
        } else {  // check all boot vols were removed
            for (URI bootVolURI : bootVolsToBeDeleted) {
                BlockObjectRestRep bootVolRep = BlockStorageUtils.getBlockResource(bootVolURI);
                if ((bootVolRep != null) && !bootVolRep.getInactive()) {
                    logError("computeutils.removebootvolumes.failure", bootVolRep.getId());
                    setPartialSuccess();
                }
            }
        }

    }

    /**
     * @return the hostURIMap
     */
    public Map<URI, String> getHostURIMap() {
        return hostURIMap;
    }

    /**
     * @param hostURIMap the hostURIMap to set
     */
    public void setHostURIMap(Map<URI, String> hostURIMap) {
        this.hostURIMap = hostURIMap;
    }

}
