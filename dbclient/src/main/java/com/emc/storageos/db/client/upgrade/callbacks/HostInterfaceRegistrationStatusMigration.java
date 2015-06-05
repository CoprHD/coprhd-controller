/**
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
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;

public class HostInterfaceRegistrationStatusMigration extends BaseCustomMigrationCallback {
    
    public static final Long FLAG_DEFAULT = 2L;
    private static final Logger log = LoggerFactory.getLogger(HostInterfaceRegistrationStatusMigration.class);
    
    @Override
    public void process() {
        processType(Initiator.class);
        processType(IpInterface.class);
    }
    
    private <T extends HostInterface> void processType(Class<T> clazz) {
        DbClient dbClient = getDbClient();
        List<URI> initiatorKeys = dbClient.queryByType(clazz, false);
        Iterator<T> hostInterfaces = dbClient.queryIterativeObjects(clazz, initiatorKeys);
        
        while (hostInterfaces.hasNext()) {
            // set default value of RegistrationStatus to REGISTERED
            HostInterface hostInterface = hostInterfaces.next();
            log.info("Setting registration status of " + hostInterface.getId() +
                    " to " + RegistrationStatus.REGISTERED);
            hostInterface.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
            dbClient.persistObject(hostInterface);
        }
    }
}

