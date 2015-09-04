/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved	
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;

public class HostInterfaceLabelMigration extends BaseCustomMigrationCallback {

    public static final Long FLAG_DEFAULT = 2L;
    private static final Logger log = LoggerFactory.getLogger(HostInterfaceLabelMigration.class);

    @Override
    public void process() {
        processInitiators();
        processIpInterfaces();
    }

    private void processInitiators() {
        DbClient dbClient = getDbClient();
        List<URI> uris = dbClient.queryByType(Initiator.class, false);
        Iterator<Initiator> objects = dbClient.queryIterativeObjects(Initiator.class, uris);

        while (objects.hasNext()) {
            Initiator initiator = objects.next();
            if (initiator.getLabel() == null || initiator.getLabel().isEmpty()) {
                log.info("Setting label of " + initiator.getId() + " to " + initiator.getInitiatorPort());
                initiator.setLabel(initiator.getInitiatorPort());
                dbClient.persistObject(initiator);
            }
        }
    }

    private void processIpInterfaces() {
        DbClient dbClient = getDbClient();
        List<URI> uris = dbClient.queryByType(IpInterface.class, false);
        Iterator<IpInterface> objects = dbClient.queryIterativeObjects(IpInterface.class, uris);

        while (objects.hasNext()) {
            IpInterface ipinterface = objects.next();
            if (ipinterface.getLabel() == null || ipinterface.getLabel().isEmpty()) {
                log.info("Setting label of " + ipinterface.getId() + " to " + ipinterface.getIpAddress());
                ipinterface.setLabel(ipinterface.getIpAddress());
                dbClient.persistObject(ipinterface);
            }
        }
    }

}
