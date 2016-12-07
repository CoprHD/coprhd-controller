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
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.google.common.collect.Lists;

@Service("RemoveHostFromCluster")
public class RemoveHostFromClusterService extends ViPRService {

    @Param(CLUSTER)
    protected URI clusterId;

    @Param(ServiceParams.HOST)
    protected List<String> ids;

    private Cluster cluster;

    private List<URI> hostIds;

    @Override
    public void precheck() throws Exception {
        StringBuilder preCheckErrors = new StringBuilder();
        hostIds = uris(ids);
        cluster = BlockStorageUtils.getCluster(clusterId);
        if (cluster == null) {
            preCheckErrors.append("Cluster doesn't exist for ID " + clusterId);
        }

        for (URI hostId : hostIds) {
            if (BlockStorageUtils.getHost(hostId) == null) {
                preCheckErrors.append("Host doesn't exist for ID " + hostId);
            } else if (!BlockStorageUtils.getHost(hostId).getCluster().equals(clusterId)) {
                String hostName = getClient().hosts().get(hostId).getHostName();
                preCheckErrors.append("Host " + hostName + " is not associated with cluster: " + cluster.getLabel());
            }
        }

        if (preCheckErrors.length() > 0) {
            throw new IllegalStateException(preCheckErrors.toString());
        }
    }

    @Override
    public void execute() throws Exception {
        // In order to remove the hosts from cluster
        // 1. Set the cluster uri = null on the host itself - Should be taken care of during deactivate host
        // 2. Delete hosts which will also take care of removing
        // Boot volumes and dissociate any shared storage that is
        // associated with the host.
        // Remove host from cluster
        // Remove hosts

        if (hostIds.isEmpty()) {
            return;
        }

        // get boot vols to be deleted (so we can check afterwards)
        List<URI> bootVolsToBeDeleted = Lists.newArrayList();
        for (URI hostURI : hostIds) {
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
        List<URI> successfulHostIds = ComputeUtils.deactivateHostURIs(hostIds);

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