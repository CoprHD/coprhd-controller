/*
 *  Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.plugins.common.PartitionManager;

/*
 * Refresh preferredSystemIds for all hosts
 */
public class ArrayAffinityPostProcessor {
    private static final Logger _logger = LoggerFactory
            .getLogger(ArrayAffinityPostProcessor.class);
    private static final List<String> HOST_PROPERTIES =
            Arrays.asList("preferredSystemIds", "label");

    private static final String HOST = "Host";
    private static final int BATCH_SIZE = 100;

    private PartitionManager _partitionManager;

    public void setPartitionManager(PartitionManager partitionManager) {
        _partitionManager = partitionManager;
    }

    public void updatePreferredSystems(Map<URI, Set<UnManagedExportMask>> hostUnManagedExportMasks, URI systemId, DbClient dbClient) {
        List<Host> hostsToUpdate = new ArrayList<Host>();

        try {
            List<URI> hostURIs = dbClient.queryByType(Host.class, true);
            Iterator<Host> hosts =
                    dbClient.queryIterativeObjectFields(Host.class, HOST_PROPERTIES, hostURIs);
            while (hosts.hasNext()) {
                Host host = hosts.next();
                if (host != null) {
                    _logger.info("Processing host {}", host.getLabel());
                    StringSet existingPreferredSystems = host.getPreferredSystemIds();
                    boolean notPreferredSystem = true;
                    String systemIdStr = systemId.toString();
                    if (hostUnManagedExportMasks != null && !hostUnManagedExportMasks.isEmpty()) {
                        Set<UnManagedExportMask> masks = hostUnManagedExportMasks.get(host.getId());
                        if (masks != null && !masks.isEmpty() && allUnKnownVolumesInMasks(masks)) {
                            if (!existingPreferredSystems.contains(systemIdStr)) {
                                notPreferredSystem = false;
                                existingPreferredSystems.add(systemIdStr);
                                hostsToUpdate.add(host);
                            }
                        }
                    }

                    if (notPreferredSystem && existingPreferredSystems.contains(systemIdStr)) {
                        existingPreferredSystems.remove(systemIdStr);
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

    /**
     * Check if all volumes in mask are unknown.
     *
     * @param masks UnManagedExportMasks to check
     * @return true if all volumes are unknown in at least one of the masks
     */
    private boolean allUnKnownVolumesInMasks(Set<UnManagedExportMask> masks) {
        for (UnManagedExportMask mask : masks) {
            _logger.info("Processing unmanaged export mask {}", mask.getMaskName());
            if (mask != null && !mask.getInactive()) {
                // all volumes in the mask are unknown to ViPR
                if (mask.getHasUnknownVolume() && mask.getKnownVolumeUris().isEmpty()) {
                    return true;
                }
            }
        }

        return false;
    }
}