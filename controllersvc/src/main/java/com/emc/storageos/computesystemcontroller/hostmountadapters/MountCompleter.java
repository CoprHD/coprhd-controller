/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.hostmountadapters;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.computesystemcontroller.impl.ComputeSystemCompleter;
import com.emc.storageos.computesystemcontroller.impl.HostCompleter;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class MountCompleter extends ComputeSystemCompleter {

    /**
     * Reference to logger
     */
    private static final Logger _logger = LoggerFactory
            .getLogger(HostCompleter.class);

    public MountCompleter(URI id, String opId) {
        super(Host.class, id, false, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        switch (status) {
            case error:
                dbClient.error(Cluster.class, this.getId(), getOpId(), coded);
                break;
            default:
                dbClient.ready(Cluster.class, this.getId(), getOpId());
        }
    }

}