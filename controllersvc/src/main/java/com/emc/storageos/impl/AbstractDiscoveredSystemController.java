/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.impl;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.db.exceptions.RetryableDatabaseException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.impl.Dispatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public abstract class AbstractDiscoveredSystemController {
    private static final Logger _log = LoggerFactory.getLogger(AbstractDiscoveredSystemController.class);

    protected void queueTask(DbClient dbClient, Class<? extends DiscoveredSystemObject> systemClazz,
            Dispatcher dispatcher, String methodName, Object... args)
    {
        final URI systemURI = (URI) args[0];
        _log.info("System {} received RMI request {}.", systemURI, methodName);
        try {
            // 1. select target device
            final DiscoveredSystemObject device = dbClient.queryObject(systemClazz, systemURI);
            final Controller controller = lookupDeviceController(device);

            // 2. queue request
            dispatcher.queue(device.getId(), device.getSystemType(), controller, methodName, args);
        } catch (RetryableDatabaseException e) {
            if (e.getServiceCode() == ServiceCode.DBSVC_CONNECTION_ERROR) {
                // netflix curator ConnectionException is not serializable
                // and thus should not be sent back to rmi client.
                _log.error("Failed to queue task due to dbsvc disconnected. Error: ", e);
                throw DatabaseException.retryables.connectionFailed();
            }
            throw e;
        }
    }

    protected abstract Controller lookupDeviceController(DiscoveredSystemObject device);
}
