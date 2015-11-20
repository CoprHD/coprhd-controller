/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.computesystemcontroller.impl;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.computesystemcontroller.impl.adapter.HostStateChange;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class ProcessHostChangesCompleter extends TaskCompleter {

    private static final Logger _logger = LoggerFactory.getLogger(ProcessHostChangesCompleter.class);
    private static final long serialVersionUID = 1L;

    protected List<HostStateChange> changes;
    protected List<URI> deletedHosts;
    protected List<URI> deletedClusters;

    public ProcessHostChangesCompleter(List<HostStateChange> changes, List<URI> deletedHosts, List<URI> deletedClusters, String opId) {
        super();
        this.changes = changes;
        this.deletedHosts = deletedHosts;
        this.deletedClusters = deletedClusters;
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        if (isNotifyWorkflow()) {
            // If there is a workflow, update the step to complete.
            updateWorkflowStatus(status, coded);
        }

        // if export updates were successful, remove all old initiators and deleted hosts
        if (status.equals(Status.ready)) {
            for (HostStateChange hostChange : changes) {
                for (URI initiatorId : hostChange.getOldInitiators()) {
                    Initiator initiator = dbClient.queryObject(Initiator.class, initiatorId);
                    dbClient.markForDeletion(initiator);
                    _logger.info("Initiator marked for deletion: " + this.getId());
                }
            }

            for (URI hostId : deletedHosts) {
                Host host = dbClient.queryObject(Host.class, hostId);
                // don't delete host if it was provisioned by Vipr
                if (!NullColumnValueGetter.isNullURI(host.getComputeElement())) {
                    _logger.info("do not delete provisioned host {} - disassociate it from vcenter", host.getLabel());
                    host.setVcenterDataCenter(NullColumnValueGetter.getNullURI());
                    dbClient.persistObject(host);
                } else if (!NullColumnValueGetter.isNullURI(host.getBootVolumeId())) {
                    _logger.info("do not delete host with boot volume {} - disassociate it from vcenter", host.getLabel());
                    host.setVcenterDataCenter(NullColumnValueGetter.getNullURI());
                    dbClient.persistObject(host);
                } else {
                    ComputeSystemHelper.doDeactivateHost(dbClient, host);
                    _logger.info("Deactivating Host: " + host.getId());
                }
            }

            for (URI clusterId : deletedClusters) {
                Cluster cluster = dbClient.queryObject(Cluster.class, clusterId);
                List<URI> clusterHosts = ComputeSystemHelper.getChildrenUris(dbClient, clusterId, Host.class, "cluster");
                // don't delete cluster if auto-exports are disabled or all hosts weren't deleted (ex: hosts provisioned by ViPR)
                if (!cluster.getAutoExportEnabled()) {
                    _logger.info("do not delete cluster {} - auto exports are disabled", cluster.getLabel());
                } else if (!clusterHosts.isEmpty()) {
                    _logger.info("do not delete cluster {} - it still has hosts - disassociate it from vcenter", cluster.getLabel());
                    cluster.setVcenterDataCenter(NullColumnValueGetter.getNullURI());
                    cluster.setExternalId(NullColumnValueGetter.getNullStr());
                    dbClient.persistObject(cluster);
                } else {
                    ComputeSystemHelper.doDeactivateCluster(dbClient, cluster);
                    _logger.info("Deactivating Cluster: " + cluster.getId());
                }
            }
        }
    }
}
