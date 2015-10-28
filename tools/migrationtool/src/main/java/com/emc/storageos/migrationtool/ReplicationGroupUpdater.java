/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.migrationtool;

import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplicationGroupUpdater extends Executor {

    private static final Logger log = LoggerFactory.getLogger(ReplicationGroupUpdater.class);

    @Override
    public boolean execute() {
        try {
            String ipAddress = null;
            String portNumber = null;
            String userName = null;
            String password = null;
            boolean usessl = false;
            WBEMClient client = getCimClient(ipAddress, portNumber, userName, password, usessl);
        } catch (Exception ex) {
            log.error("Exception occurred while updateing replication groups.", ex);
        }
        return false;
    }
}
