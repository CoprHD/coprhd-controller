/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute;

import static com.emc.sa.service.ServiceParams.CLUSTER;

import java.net.URI;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.compute.tasks.DeactivateCluster;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.google.common.collect.Lists;

@Service("RemoveComputeCluster")
public class RemoveComputeClusterService extends ViPRService {

    @Param(CLUSTER)
    protected URI clusterId;

    private Cluster cluster;

    private List<URI> provisionedHostURIs = null;
    private List<URI> hostURIs = null;
    @Override
    public void precheck() throws Exception {
        StringBuilder preCheckErrors = new StringBuilder();
        cluster = BlockStorageUtils.getCluster(clusterId);
        if (cluster == null) {
            preCheckErrors.append("Cluster doesn't exist for ID " + clusterId);
        }
        hostURIs = ComputeUtils.getHostURIsByCluster(getClient(), clusterId);
        provisionedHostURIs = ComputeUtils.getProvisionedHostURIsByCluster(clusterId);

        if (!CollectionUtils.isEmpty(hostURIs) && !CollectionUtils.isEmpty(provisionedHostURIs)
                && (hostURIs.size() > provisionedHostURIs.size() || !provisionedHostURIs.containsAll(hostURIs))) {
            logError("computeutils.deactivatecluster.deactivate.notpossible", cluster.getLabel());
            preCheckErrors.append("Cluster ").append(cluster.getLabel())
            .append(" is a mixed cluster, cannot decommission a mixed cluster.");
        }
        if (preCheckErrors.length() > 0) {
            throw new IllegalStateException(preCheckErrors.toString());
        }
    }

    @Override
    public void execute() throws Exception {

        // removing cluster checks for running VMs first for ESX hosts
        addAffectedResource(clusterId);

        if (provisionedHostURIs.isEmpty() && hostURIs.isEmpty()) {
            execute(new DeactivateCluster(cluster));
            return;
        }

        // get boot vols to be deleted (so we can check afterwards)
        List<URI> bootVolsToBeDeleted = Lists.newArrayList();
        for (URI hostURI : provisionedHostURIs) {
            URI bootVolURI = BlockStorageUtils.getHost(hostURI).getBootVolumeId();
            if (bootVolURI != null) {
                BlockObjectRestRep bootVolRep = null;
                try{
                    bootVolRep = BlockStorageUtils.getBlockResource(bootVolURI);
                } catch(Exception e){
                    //Invalid boot volume reference. Ignore
                }
                if (bootVolRep!=null && !bootVolRep.getInactive()) {
                    bootVolsToBeDeleted.add(bootVolURI);
                }
            }
        }

        // removing hosts also removes associated boot volumes and exports
        List<URI> successfulHostIds = ComputeUtils.deactivateHostURIs(provisionedHostURIs);

        // fail order if no hosts removed
        if (successfulHostIds.isEmpty()) {
            throw new IllegalStateException(ExecutionUtils.getMessage("computeutils.deactivatehost.deactivate.failure", ""));
        }
        // Check if all hosts are deactivated successfully and only then issue the deactivateCluster call
        if (successfulHostIds.size() == provisionedHostURIs.size() && successfulHostIds.containsAll(provisionedHostURIs)) {
            execute(new DeactivateCluster(cluster));
        }

        // check all hosts were removed
        if (successfulHostIds.size() < provisionedHostURIs.size()) {
            for (URI hostURI : provisionedHostURIs) {
                if (!successfulHostIds.contains(hostURI)) {
                    logError("computeutils.deactivatehost.failure", hostURI, clusterId);
                }
            }
            setPartialSuccess();
        }
        else {  // check all boot vols were removed
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
     * @return the provisionedHostURIs
     */
    public List<URI> getProvisionedHostURIs() {
        return provisionedHostURIs;
    }

    /**
     * @param provisionedHostURIs the provisionedHostURIs to set
     */
    public void setProvisionedHostURIs(List<URI> provisionedHostURIs) {
        this.provisionedHostURIs = provisionedHostURIs;
    }

    /**
     * @return the hostURIs
     */
    public List<URI> getHostURIs() {
        return hostURIs;
    }

    /**
     * @param hostURIs the hostURIs to set
     */
    public void setHostURIs(List<URI> hostURIs) {
        this.hostURIs = hostURIs;
    }
}