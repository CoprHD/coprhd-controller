/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute;

import static com.emc.sa.service.ServiceParams.CLUSTER;
import static com.emc.sa.service.ServiceParams.DATACENTER;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.VCENTER;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.VcenterDataCenter;

@Service("UpdateVcenterCluster")
public class UpdateVcenterClusterService extends ViPRService {

    @Param(CLUSTER)
    protected URI clusterId;

    @Param(PROJECT)
    protected URI project;

    @Param(VCENTER)
    protected URI vcenterId;

    @Param(DATACENTER)
    protected URI datacenterId;

    @Override
    public void precheck() throws Exception {

        StringBuilder preCheckErrors = new StringBuilder();

        Cluster cluster = BlockStorageUtils.getCluster(clusterId);
        if (cluster == null) {
            preCheckErrors.append(
                    ExecutionUtils.getMessage("compute.vcenter.cluster.does.not.exist.update", clusterId) + " ");
        } else {
            BlockStorageUtils.checkEvents(cluster);
            acquireClusterLock(cluster);
        }

        if (preCheckErrors.length() > 0) {
            throw new IllegalStateException(preCheckErrors.toString() + 
                    ComputeUtils.getContextErrors(getModelClient()));
        }
    }

    @Override
    public void execute() throws Exception {
        Cluster cluster = BlockStorageUtils.getCluster(clusterId);
        VcenterDataCenter datacenter = ComputeUtils.getVcenterDataCenter(datacenterId);

        // If the cluster already has a datacenter associated with it,
        // it needs to be updated, else create.
        URI existingDatacenterId = cluster.getVcenterDataCenter();
        boolean status = false;
        if (existingDatacenterId == null) {
            logInfo("vcenter.cluster.create", cluster.getLabel());
            if (datacenter == null) {
                status = ComputeUtils.createVcenterCluster(cluster, datacenterId);
            } else {
                status = ComputeUtils.createVcenterCluster(cluster, datacenter);
            }
            if (!status) {
                throw new IllegalStateException(
                        ExecutionUtils.getMessage("vcenter.cluster.create.failed", cluster.getLabel() + " "));
            }
        }
        else {
            logInfo("vcenter.cluster.update", cluster.getLabel());
            if (datacenter == null) {
                status = ComputeUtils.updateVcenterCluster(cluster, datacenterId);
            } else {
                status = ComputeUtils.updateVcenterCluster(cluster, datacenter);
            }
            if (!status) {
                throw new IllegalStateException(
                        ExecutionUtils.getMessage("vcenter.cluster.update.failed", cluster.getLabel() + " "));
            }
        }
    }

    public URI getClusterId() {
        return clusterId;
    }

    public void setClusterId(URI clusterId) {
        this.clusterId = clusterId;
    }

    public URI getProject() {
        return project;
    }

    public void setProject(URI project) {
        this.project = project;
    }

    public URI getVcenterId() {
        return vcenterId;
    }

    public void setVcenterId(URI vcenterId) {
        this.vcenterId = vcenterId;
    }

    public URI getDatacenterId() {
        return datacenterId;
    }

    public void setDatacenterId(URI datacenterId) {
        this.datacenterId = datacenterId;
    }
}
