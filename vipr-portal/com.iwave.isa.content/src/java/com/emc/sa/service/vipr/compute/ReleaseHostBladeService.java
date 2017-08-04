/*
 * Copyright (c) 2016-2017
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute;

import static com.emc.sa.service.ServiceParams.ASSOCIATE_HOST_COMPUTE_ELEMENT;
import static com.emc.sa.service.ServiceParams.ASSOCIATE_HOST_COMPUTE_VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.CLUSTER;
import static com.emc.sa.service.ServiceParams.HOST_COMPUTE_VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.RELEASE_CONFIRM;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.compute.tasks.ReleaseHostComputeElementTask;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.vipr.client.Task;
@Service("ReleaseHostBlade")
public class ReleaseHostBladeService extends ViPRService {

    @Param(CLUSTER)
    private URI clusterId;

    @Param(ServiceParams.HOST)
    private URI hostId;

    @Param(value = HOST_COMPUTE_VIRTUAL_POOL, required =false)
    private URI hostComputeVPool;

    @Param(value = ASSOCIATE_HOST_COMPUTE_VIRTUAL_POOL, required = false)
    private URI associateHostComputeVPool;

    @Param(value = ASSOCIATE_HOST_COMPUTE_ELEMENT, required = false)
    private URI associateHostComputeElement;

    private Cluster cluster;
    private Host host;
    private URI computeSystemURI;

    @Param(value = RELEASE_CONFIRM, required = true)
    protected boolean releaseConfirm;

    @Override
    public void precheck() throws Exception {
        StringBuilder preCheckErrors = new StringBuilder();
        if(!releaseConfirm) {
            preCheckErrors.append("Confirm checkbox is unselected, please confirm by clicking the checkbox.");
            throw new IllegalStateException(
                    preCheckErrors.toString() + ComputeUtils.getContextErrors(getModelClient()));
        }
        cluster = BlockStorageUtils.getCluster(getClusterId());
        if (cluster == null) {
            preCheckErrors.append("Cluster doesn't exist for ID " + clusterId + " ");
        }
        host = BlockStorageUtils.getHost(getHostId());
        if (host == null) {
            preCheckErrors.append("Host doesn't exist for ID " + hostId);
            throw new IllegalStateException(
                    preCheckErrors.toString() + ComputeUtils.getContextErrors(getModelClient()));
        } else if (!host.getCluster().equals(clusterId)) {
            preCheckErrors
                    .append("Host " + host.getLabel() + " is not associated with cluster: " + cluster.getLabel() + " ");
        } else if (NullColumnValueGetter.isNullURI(host.getServiceProfile())) {
            preCheckErrors.append("Cannot change compute element for non-vBlock host - ");
            preCheckErrors.append(host.getLabel());
            preCheckErrors.append(".");
        }

        if (NullColumnValueGetter.isNullURI(host.getComputeElement())) {
            preCheckErrors.append(
                    ExecutionUtils.getMessage("releaseAssociate.computeElement.not.associated", host.getLabel()));
        }

        if (preCheckErrors.length() > 0) {
            throw new IllegalStateException(
                    preCheckErrors.toString() + ComputeUtils.getContextErrors(getModelClient()));
        }

    }

    @Override
    public void execute() throws Exception {
        performReleaseAction();
    }

    private Task<HostRestRep> performReleaseAction() {
        ExecutionUtils.currentContext().logInfo("releaseAssociate.computeElement.release.action", host.getLabel());
        Task<HostRestRep> releaseTask = execute(new ReleaseHostComputeElementTask(hostId));
        addAffectedResource(releaseTask);
        ExecutionUtils.currentContext().logInfo("releaseAssociate.computeElement.release.action.success",
                host.getLabel());
        return releaseTask;
    }

    /**
     * @return the clusterId
     */
    public URI getClusterId() {
        return clusterId;
    }

    /**
     * @return the hostId
     */
    public URI getHostId() {
        return hostId;
    }

    /**
     * @return the hostComputeVPool
     */
    public URI getHostComputeVPool() {
        return hostComputeVPool;
    }

    /**
     * @return the associateHostComputeVPool
     */
    public URI getAssociateHostComputeVPool() {
        return associateHostComputeVPool;
    }

    /**
     * @return the associateHostComputeElement
     */
    public URI getAssociateHostComputeElement() {
        return associateHostComputeElement;
    }

    /**
     * @return the cluster
     */
    public Cluster getCluster() {
        return cluster;
    }

    /**
     * @param cluster the cluster to set
     */
    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

    /**
     * @return the host
     */
    public Host getHost() {
        return host;
    }

    /**
     * @param host the host to set
     */
    public void setHost(Host host) {
        this.host = host;
    }

    /**
     * @return the computeSystemURI
     */
    public URI getComputeSystemURI() {
        return computeSystemURI;
    }
}
