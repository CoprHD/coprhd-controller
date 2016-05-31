/*
 *  Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.plugins.common.PartitionManager;

/*
 * Refresh preferredPoolIds for all hosts
 */
public class ArrayAffinityPostProcessor {
    private static final Logger _logger = LoggerFactory
            .getLogger(ArrayAffinityPostProcessor.class);
    private static final List<String> HOST_PROPERTIES = Arrays.asList("preferredPoolIds", "label");

    private static final String HOST = "Host";
    private static final int BATCH_SIZE = 100;

    private PartitionManager _partitionManager;

    public void setPartitionManager(PartitionManager partitionManager) {
        _partitionManager = partitionManager;
    }

    public void updatePreferredPoolIds(Map<URI, Set<String>> hostUnManagedExportMasks,
            Map<String, Set<URI>> maskStroagePools, URI systemId, DbClient dbClient) {
        List<Host> hostsToUpdate = new ArrayList<Host>();

        try {
            List<URI> hostURIs = dbClient.queryByType(Host.class, true);
            Iterator<Host> hosts =
                    dbClient.queryIterativeObjectFields(Host.class, HOST_PROPERTIES, hostURIs);
            while (hosts.hasNext()) {
                Host host = hosts.next();
                if (host != null) {
                    _logger.info("Processing host {}", host.getLabel());
                    Set<URI> preferredPoolURIs = new HashSet<URI>();
                    if (hostUnManagedExportMasks != null && !hostUnManagedExportMasks.isEmpty()) {
                        Set<String> masks = hostUnManagedExportMasks.get(host.getId());
                        if (masks != null && !masks.isEmpty()) {
                            for (String mask : masks) {
                                Set<URI> pools = maskStroagePools.get(mask);
                                if (pools != null && !pools.isEmpty()) {
                                    preferredPoolURIs.addAll(pools);
                                }
                            }
                        }
                    }

                    if (ArrayAffinityDiscoveryUtils.updatePreferredPools(host, systemId, dbClient, preferredPoolURIs)) {
                        hostsToUpdate.add(host);
                    }
                }

                // if hostsToUpdate size reaches BATCH_SIZE, persist to db
                if (hostsToUpdate.size() >= BATCH_SIZE) {
                    _partitionManager.updateInBatches(hostsToUpdate, BATCH_SIZE, dbClient, HOST);
                    hostsToUpdate.clear();
                }
            }

            if (!hostsToUpdate.isEmpty()) {
                _partitionManager.updateInBatches(hostsToUpdate, BATCH_SIZE, dbClient, HOST);
            }
        } catch (Exception e) {
            _logger.warn("Exception on updatePreferredSystems {}", e.getMessage());
        }
    }
}