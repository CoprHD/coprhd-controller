/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class IpInterfaceCompleter extends ComputeSystemCompleter {

    private static final Logger _logger = LoggerFactory
            .getLogger(IpInterfaceCompleter.class);

    public IpInterfaceCompleter(URI ipId, boolean deactivateOnComplete, String opId) {
        super(IpInterface.class, ipId, deactivateOnComplete, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        super.complete(dbClient, status, coded);
        switch (status) {
            case error:
                dbClient.error(IpInterface.class, this.getId(), getOpId(), coded);
                break;
            default:
                dbClient.ready(IpInterface.class, this.getId(), getOpId());
        }

        if (deactivateOnComplete && status.equals(Status.ready)) {
            IpInterface ipinterface = dbClient.queryObject(IpInterface.class, this.getId());
            dbClient.markForDeletion(ipinterface);
            _logger.info("IpInterface marked for deletion: " + this.getId());
        }
    }

}
