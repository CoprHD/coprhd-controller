/*
 * Copyright (c) 2016-2017
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute;

import static com.emc.sa.service.ServiceParams.ASSOCIATE_HOST_COMPUTE_ELEMENT;
import static com.emc.sa.service.ServiceParams.ASSOCIATE_HOST_COMPUTE_VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.CLUSTER;
import static com.emc.sa.service.ServiceParams.HOST_PREVIOUS_COMPUTE_VIRTUAL_POOL;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.compute.tasks.AssociateHostComputeElementTask;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.vipr.client.Task;
@Service("AssociateHostBlade")
public class AssociateHostBladeService extends ViPRService {

    @Param(CLUSTER)
    protected URI clusterId;

    @Param(ServiceParams.HOST)
    protected URI hostId;

    @Param(HOST_PREVIOUS_COMPUTE_VIRTUAL_POOL)
    protected URI hostPreviousComputeVPool;

    @Param(value = ASSOCIATE_HOST_COMPUTE_VIRTUAL_POOL, required = true)
    protected URI associateHostComputeVPool;

    @Param(value = ASSOCIATE_HOST_COMPUTE_ELEMENT, required = true)
    protected URI associateHostComputeElement;

    private Cluster cluster;
    private Host host;
    private URI computeSystemURI;

    @Override
    public void precheck() throws Exception {
        StringBuilder preCheckErrors = new StringBuilder();

        cluster = BlockStorageUtils.getCluster(clusterId);
        if (cluster == null) {
            preCheckErrors.append("Cluster doesn't exist for ID " + clusterId + " ");
        }
        host = BlockStorageUtils.getHost(hostId);
        if (host == null) {
            preCheckErrors.append("Host doesn't exist for ID " + hostId);
            throw new IllegalStateException(
                    preCheckErrors.toString() + ComputeUtils.getContextErrors(getModelClient()));
        } else if (!host.getCluster().equals(clusterId)) {
            preCheckErrors
                    .append("Host " + host.getLabel() + " is not associated with cluster: " + cluster.getLabel() + " ");
        } else if (NullColumnValueGetter.isNullURI(host.getComputeVirtualPoolId())
                || NullColumnValueGetter.isNullURI(host.getServiceProfile())) {
            preCheckErrors.append("Cannot change compute element for non-vBlock host - ");
            preCheckErrors.append(host.getLabel());
            preCheckErrors.append(".");
        }

        if (!NullColumnValueGetter.isNullURI(host.getComputeElement())) {
            preCheckErrors.append(ExecutionUtils.getMessage("releaseAssociate.computeElement.not.released.state",
                    host.getLabel(), host.getComputeElement()));
        } else {
            if (NullColumnValueGetter.isNullURI(associateHostComputeVPool)
                    || NullColumnValueGetter.isNullURI(associateHostComputeElement)) {
                preCheckErrors.append(
                        ExecutionUtils.getMessage("releaseAssociate.computeElement.associate.insufficient.input"));
            } else {
                ComputeElementRestRep newCE = getClient().computeElements().get(associateHostComputeElement);
                computeSystemURI = newCE.getComputeSystem().getId();
            }
        }
        if (preCheckErrors.length() > 0) {
            throw new IllegalStateException(
                    preCheckErrors.toString() + ComputeUtils.getContextErrors(getModelClient()));
        }
    }

    @Override
    public void execute() throws Exception {
        ComputeElementRestRep newCE = ComputeUtils.getComputeElement(getClient(), associateHostComputeElement);
        ExecutionUtils.currentContext().logInfo("releaseAssociate.computeElement.associate.action", host.getLabel(),
                newCE != null ? newCE.getName() : associateHostComputeElement);
        Task<HostRestRep> associateTask = execute(new AssociateHostComputeElementTask(hostId, associateHostComputeVPool,
                associateHostComputeElement, computeSystemURI));
        addAffectedResource(associateTask);
        ExecutionUtils.currentContext().logInfo("releaseAssociate.computeElement.associate.action.success", host.getLabel(),
                newCE != null ? newCE.getName() : associateHostComputeElement);
    }
}
