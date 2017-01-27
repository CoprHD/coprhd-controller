/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ActionableEvent;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

@SuppressWarnings("serial")
public class HostCompleter extends ComputeSystemCompleter {

    private static final Logger _logger = LoggerFactory.getLogger(HostCompleter.class);
    private final URI eventId;
    private final Map<URI, URI> oldHostClusters = new HashMap<URI, URI>();
    private final Map<URI, URI> oldHostVcenterDataCenters = new HashMap<URI, URI>();

    public HostCompleter(URI id, boolean deactivateOnComplete, String opId) {
        this(NullColumnValueGetter.getNullURI(), id, deactivateOnComplete, opId);
    }

    public HostCompleter(List<URI> ids, boolean deactivateOnComplete, String opId) {
        this(NullColumnValueGetter.getNullURI(), ids, deactivateOnComplete, opId);
    }

    public HostCompleter(URI eventId, URI id, boolean deactivateOnComplete, String opId) {
        super(Host.class, id, deactivateOnComplete, opId);
        this.eventId = eventId;
    }

    public HostCompleter(URI eventId, List<URI> ids, boolean deactivateOnComplete, String opId) {
        super(Host.class, ids, deactivateOnComplete, opId);
        this.eventId = eventId;
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        super.complete(dbClient, status, coded);
        for (URI id : getIds()) {
            switch (status) {
                case error:
                    Host host = dbClient.queryObject(Host.class, id);
                    if (!NullColumnValueGetter.isNullURI(host.getComputeElement())) {
                        host.setProvisioningStatus(Host.ProvisioningJobStatus.ERROR.toString());
                        dbClient.updateObject(host);
                    }
                    if (!NullColumnValueGetter.isNullURI(eventId)) {
                        ActionableEvent event = dbClient.queryObject(ActionableEvent.class, eventId);
                        if (event != null) {
                            event.setEventStatus(ActionableEvent.Status.failed.name());
                            dbClient.updateObject(event);
                        }
                    }

                    if (oldHostClusters.containsKey(id)) {
                        _logger.info(String.format("Updating cluster to %s for host %s", oldHostClusters.get(id), id));
                        ComputeSystemHelper.updateHostAndInitiatorClusterReferences(dbClient, oldHostClusters.get(id), id);
                    }

                    if (oldHostVcenterDataCenters.containsKey(id)) {
                        _logger.info(String.format("Updating vcenter datacenter to %s for host %s", oldHostVcenterDataCenters.get(id), id));
                        ComputeSystemHelper.updateHostVcenterDatacenterReference(dbClient, id, oldHostVcenterDataCenters.get(id));
                    }

                    dbClient.error(Host.class, id, getOpId(), coded);
                    break;
                default:
                    dbClient.ready(Host.class, id, getOpId());
            }

            if (deactivateOnComplete && status.equals(Status.ready)) {
                Host host = dbClient.queryObject(Host.class, id);
                ComputeSystemHelper.doDeactivateHost(dbClient, host);
                _logger.info("Deactivating Host: " + id);
            }
        }
    }

    /**
     * Used to set the "old" values for cluster and vcenter data center. These values will be used for
     * rollback if an error occurs.
     *
     * @param hostUri the rollback host URI
     * @param oldClusterUri the rollback cluster URI
     * @param oldVcenterDataCenterUri the rollback vcenter data center URI
     */
    public void addOldHostClusterAndVcenterDataCenter(URI hostUri, URI oldClusterUri, URI oldVcenterDataCenterUri) {
        oldHostClusters.put(hostUri, oldClusterUri);
        oldHostVcenterDataCenters.put(hostUri, oldVcenterDataCenterUri);
    }
}
