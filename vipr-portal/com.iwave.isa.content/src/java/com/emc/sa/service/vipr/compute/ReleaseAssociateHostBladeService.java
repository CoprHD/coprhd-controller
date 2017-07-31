/*
 * Copyright (c) 2016-2017
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute;

import static com.emc.sa.service.ServiceParams.ASSOCIATE_HOST_COMPUTE_ELEMENT;
import static com.emc.sa.service.ServiceParams.ASSOCIATE_HOST_COMPUTE_VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.CLUSTER;
import static com.emc.sa.service.ServiceParams.HOST_COMPUTE_VIRTUAL_POOL;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.compute.tasks.AssociateHostComputeElementTask;
import com.emc.sa.service.vipr.compute.tasks.ReleaseHostComputeElementTask;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.vipr.client.Task;
@Service("ReleaseAssociateHostBlade")
public class ReleaseAssociateHostBladeService extends ViPRService {

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

    private enum Enum {
        RELEASE, ASSOCIATE, BOTH
    };

    private Enum operation;

    @Override
    public void precheck() throws Exception {
        StringBuilder preCheckErrors = new StringBuilder();
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
        } else {
            if (NullColumnValueGetter.isNullURI(getAssociateHostComputeVPool())
                    && NullColumnValueGetter.isNullURI(getAssociateHostComputeElement())) {
                // no associate blade step required, only release blade.
                operation = Enum.RELEASE;
            } else {
                // both release and associate steps need to be performed.
                ComputeElementRestRep newCE = getClient().computeElements().get(getAssociateHostComputeElement());
                computeSystemURI = newCE.getComputeSystem().getId();
                operation = Enum.BOTH;
            }
        }

    }

    @Override
    public void execute() throws Exception {
        switch (operation) {
        case RELEASE:
            performReleaseAction();
            break;
        case BOTH:
            Task<HostRestRep> releaseTask = performReleaseAction();
            Task<HostRestRep> associateTask = null;
            ComputeElementRestRep newCE = ComputeUtils.getComputeElement(getClient(), associateHostComputeElement);
            try {
                ExecutionUtils.currentContext().logInfo("releaseAssociate.computeElement.associate.action",
                        host.getLabel(), newCE != null ? newCE.getName() : associateHostComputeElement);
                associateTask = execute(new AssociateHostComputeElementTask(hostId, associateHostComputeVPool,
                        associateHostComputeElement, getComputeSystemURI()));
                addAffectedResource(associateTask);
                ExecutionUtils.currentContext().logInfo("releaseAssociate.computeElement.associate.action.success",
                        host.getLabel(), newCE != null ? newCE.getName() : associateHostComputeElement);
            } catch (Exception ex) {
                ExecutionUtils.currentContext().logError(ex, "releaseAssociate.computeElement.associate.action.error",
                        host.getLabel(), newCE != null ? newCE.getName() : associateHostComputeElement);
            }

            if (releaseTask.isComplete() && associateTask != null && associateTask.isError()) {
                setPartialSuccess();
            }
            break;
        default:
            // do nothing
            break;
        }
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
