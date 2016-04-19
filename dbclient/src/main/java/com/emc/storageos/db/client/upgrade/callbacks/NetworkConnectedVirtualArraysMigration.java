/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * This class is perform necessary updates of network connected virtual
 * array as the definition of this field changed between 1.1 and 2.0.
 * 
 */
public class NetworkConnectedVirtualArraysMigration extends
        BaseCustomMigrationCallback {

    private static final Logger log = LoggerFactory.getLogger(NetworkConnectedVirtualArraysMigration.class);

    /**
     * Add assigned virtual arrays to connected virtual arrays.
     * Starting 2.0, connected virtual arrays includes assigned,
     * connected via port-virtualArray associations, and connected
     * via routed networks
     */
    @Override
    public void process() throws MigrationCallbackException {
        DbClient dbClient = getDbClient();

        try {
            List<URI> networkUris = dbClient.queryByType(Network.class, true);
            Iterator<Network> networks =
                    dbClient.queryIterativeObjects(Network.class, networkUris);
            List<Network> updated = new ArrayList<Network>();
            while (networks.hasNext()) {
                Network network = networks.next();
                log.info("Updating connected virtual arrays for network {}", network.getLabel());
                if (network.getAssignedVirtualArrays() != null) {
                    if (network.getConnectedVirtualArrays() != null) {
                        for (String assignedVarrayUri : network.getAssignedVirtualArrays()) {
                            log.info("Adding virtual array {} to connected virtual arrays", assignedVarrayUri);
                            network.getConnectedVirtualArrays().add(assignedVarrayUri);
                        }
                    } else {
                        log.info("Setting connected virtual arrays to {}", network.getAssignedVirtualArrays());
                        network.setConnectedVirtualArrays(new StringSet(network.getAssignedVirtualArrays()));
                    }
                    updated.add(network);
                }
            }
            dbClient.updateAndReindexObject(updated);
        } catch (Exception e) {
            log.error("Exception occured while updating Network connectedVirtualArrays");
            log.error(e.getMessage(), e);
        }
    }
}
