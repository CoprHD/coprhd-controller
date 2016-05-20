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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
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
    private static final List<String> UNMANAGED_EXPORT_MASK_PROPERTIES =
            Arrays.asList("storageSystem", "knownVolumeUris", "hasUnknownVolume", "inactive");

    private static final String HOST = "Host";
    private static final int BATCH_SIZE = 100;

    private PartitionManager _partitionManager;

    public void setPartitionManager(PartitionManager partitionManager) {
        _partitionManager = partitionManager;
    }

    public void updatePreferredSystems(DbClient dbClient) {
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
                    StringSet newPreferredSystems = findPreferredSystems(host.getId(), dbClient);
                    if (!newPreferredSystems.equals(existingPreferredSystems)) {
                        host.getPreferredSystemIds().replace(newPreferredSystems);
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
     * Returns a set of Ids of storage systems which have storage mapped to the given host.
     *
     * @param hostUri the Host URI to check
     * @param dbClient a reference to the database client
     * @return a set of Ids of storage systems
     */
    private StringSet findPreferredSystems(URI hostURI, DbClient dbClient) {
        _logger.info("Finding unmanaged export masks for host " + hostURI);
        List<Initiator> initiators = ComputeSystemHelper.queryInitiators(dbClient, hostURI);

        StringSet preferredSystems = new StringSet();
        URIQueryResultList results = new URIQueryResultList();
        for (Initiator initiator : initiators) {
            _logger.info("Looking at initiator " + initiator.getInitiatorPort());
            dbClient.queryByConstraint(
                    AlternateIdConstraint.Factory.getUnManagedExportMaskKnownInitiatorConstraint(initiator.getInitiatorPort()), results);
            if (results.iterator() != null) {
                Iterator<UnManagedExportMask> masks =
                        dbClient.queryIterativeObjectFields(UnManagedExportMask.class,
                                UNMANAGED_EXPORT_MASK_PROPERTIES, results);
                while (masks.hasNext()) {
                    UnManagedExportMask mask = masks.next();
                    _logger.info("Processing unmanaged export mask {}", mask.getMaskName());
                    if (mask != null && !mask.getInactive()) {
                        // all volumes in the mask are unknown to ViPR
                        if (mask.getHasUnknownVolume() && mask.getKnownVolumeUris().isEmpty()) {
                            preferredSystems.add(mask.getStorageSystemUri().toString());
                        }
                    }
                }
            }
        }

        return preferredSystems;
    }
}