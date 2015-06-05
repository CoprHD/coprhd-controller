/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class HostCompleter extends ComputeSystemCompleter {

    private static final Logger _logger = LoggerFactory.getLogger(HostCompleter.class);

    public HostCompleter(URI id, boolean deactivateOnComplete, String opId) {
        super(Host.class, id, deactivateOnComplete, opId);
    }

    public HostCompleter(List<URI> ids, boolean deactivateOnComplete, String opId) {
        super(Host.class, ids, deactivateOnComplete, opId);
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
                    dbClient.persistObject(host);
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

}
