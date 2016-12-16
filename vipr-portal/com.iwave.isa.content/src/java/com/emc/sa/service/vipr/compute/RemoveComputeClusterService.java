/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute;

import static com.emc.sa.service.ServiceParams.CLUSTER;

import java.net.URI;
import java.util.List;

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

    @Override
    public void precheck() throws Exception {
        StringBuilder preCheckErrors = new StringBuilder();
        cluster = BlockStorageUtils.getCluster(clusterId);
        if (cluster == null) {
            preCheckErrors.append("Cluster doesn't exist for ID " + clusterId);
        }

        if (preCheckErrors.length() > 0) {
            throw new IllegalStateException(preCheckErrors.toString());
        }
    }

    @Override
    public void execute() throws Exception {

        List<URI> hostURIs = ComputeUtils.getHostURIsByCluster(getClient(), clusterId);

        // removing cluster checks for running VMs first for ESX hosts
        addAffectedResource(clusterId);
        execute(new DeactivateCluster(cluster));

        if (hostURIs.isEmpty()) {
            return;
        }

        // get boot vols to be deleted (so we can check afterwards)
        List<URI> bootVolsToBeDeleted = Lists.newArrayList();
        for (URI hostURI : hostURIs) {
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
        List<URI> successfulHostIds = ComputeUtils.deactivateHostURIs(hostURIs);

        // fail order if no hosts removed
        if (successfulHostIds.isEmpty()) {
            throw new IllegalStateException(ExecutionUtils.getMessage("computeutils.deactivatehost.deactivate.failure", ""));
        }

        // check all hosts were removed
        if (successfulHostIds.size() < hostURIs.size()) {
            for (URI hostURI : hostURIs) {
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

}