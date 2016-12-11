/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
*/
package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * If we are upgrading from any version 3.5 or before,
 * the associated existing initiators of the Export Mask Object needs to be moved
 * to the userAddedInitiator of the same Object for all Initiators that are part
 * introduced into ViPR.
 * 
 * @author yalamh
 * @since 3.5+
 */

public class ExportMaskExistingInitiatorsMigration extends BaseCustomMigrationCallback {
    private static final Logger logger = LoggerFactory.getLogger(ExportMaskExistingInitiatorsMigration.class);

    @Override
    public void process() throws MigrationCallbackException {

        DbClient dbClient = getDbClient();

        Map<URI, StorageSystem> systemMap = new HashMap<>();
        int pageSize = 100;
        int totalExportMaskObjectCount = 0;
        int exportMaskUpdatedCount = 0;
        URI nextId = null;
        while (true) {
            List<URI> exportMaskUris = dbClient.queryByType(ExportMask.class, true, nextId, pageSize);

            if (exportMaskUris == null || exportMaskUris.isEmpty()) {
                break;
            }

            logger.info("processing page of {} {} Objects", exportMaskUris.size(), ExportMask.class.getSimpleName());
            Iterator<ExportMask> exportMaskIterator = dbClient.queryIterativeObjects(ExportMask.class, exportMaskUris, true);

            while (exportMaskIterator.hasNext()) {
                ExportMask exportMask = exportMaskIterator.next();
                URI systemUri = exportMask.getStorageDevice();
                StorageSystem system = systemMap.get(systemUri);
                if (system == null) {
                    system = dbClient.queryObject(StorageSystem.class, systemUri);
                    if (system != null) {
                        systemMap.put(systemUri, system);
                    }
                }

                if (system != null && Type.vmax.toString().equalsIgnoreCase(system.getSystemType())) {

                    logger.info("Processing existing initiators for export mask {} on VMAX storage {}", exportMask.getId(), systemUri);
                    boolean updateObject = false;
                    List<String> initiatorsToProcess = new ArrayList<String>();

                    if (exportMask.getExistingInitiators() != null &&
                            !exportMask.getExistingInitiators().isEmpty()) {
                        initiatorsToProcess.addAll(exportMask.getExistingInitiators());
                        for (String portName : initiatorsToProcess) {
                            Initiator existingInitiator = getInitiator(Initiator.toPortNetworkId(portName), dbClient);
                            if (existingInitiator != null && !checkIfDifferentResource(exportMask, existingInitiator)) {
                                exportMask.addInitiator(existingInitiator);
                                exportMask.addToUserCreatedInitiators(existingInitiator);
                                exportMask.removeFromExistingInitiators(existingInitiator);
                                logger.info("Initiator {} is being moved from existing to userCreated for the Mask {}", portName,
                                        exportMask.forDisplay());
                                updateObject = true;
                            }
                        }
                    }
                    if (updateObject) {
                        logger.info("Processed existing initiators for export mask {} on VMAX storage {} and updated the Mask Object",
                                exportMask.getId(), systemUri);
                        dbClient.updateObject(exportMask);
                        exportMaskUpdatedCount++;
                    }
                } else if (system == null) {
                    logger.warn("could not determine storage system type for exportMask {}",
                            exportMask.forDisplay());
                }
            }

            nextId = exportMaskUris.get(exportMaskUris.size() - 1);
            totalExportMaskObjectCount += exportMaskUris.size();
        }

        logger.info("Updated Existing information on {} of {} Export Mask Objects on VMAX Storage",
                exportMaskUpdatedCount, totalExportMaskObjectCount);
    }

    /**
     * Get an initiator as specified by the initiator's network port.
     * 
     * @param networkPort The initiator's port WWN or IQN.
     * @return A reference to an initiator.
     */
    public static Initiator getInitiator(String networkPort, DbClient dbClient) {
        Initiator initiator = null;
        URIQueryResultList resultsList = new URIQueryResultList();

        // find the initiator
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getInitiatorPortInitiatorConstraint(
                networkPort), resultsList);
        Iterator<URI> resultsIter = resultsList.iterator();
        while (resultsIter.hasNext()) {
            initiator = dbClient.queryObject(Initiator.class, resultsIter.next());
            // there should be one initiator, so return as soon as it is found
            if (initiator != null && !initiator.getInactive()) {
                return initiator;
            }
        }
        return null;
    }

    /**
     * Check if the mask and the initiator belong to different resource.
     */
    public static boolean checkIfDifferentResource(ExportMask mask, Initiator existingInitiator) {
        boolean differentResource = false;
        String maskResource = mask.getResource();
        if (!NullColumnValueGetter.isNullValue(maskResource)) { // check only if the mask has resource
            if (URIUtil.isType(URI.create(maskResource), Host.class)) {
                differentResource = !maskResource.equals(existingInitiator.getHost().toString());
            } else {
                differentResource = !maskResource.equals(existingInitiator.getClusterName());
            }
        }
        return differentResource;
    }

}
