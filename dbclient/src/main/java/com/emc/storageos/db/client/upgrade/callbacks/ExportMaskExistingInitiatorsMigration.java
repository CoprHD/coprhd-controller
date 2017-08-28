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
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
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
        logger.info("Started migration for exportmask existingInitiator into userCreatedInitiator");
        try {
            DbClient dbClient = getDbClient();
            int totalExportMaskObjectCount = 0;
            int exportMaskUpdatedCount = 0;
            Map<URI, String> systemTypeMap = new HashMap<>();
            StorageSystem system = null;
            List<URI> exportMaskUris = dbClient.queryByType(ExportMask.class, true);
            Iterator<ExportMask> exportMaskIterator = dbClient.queryIterativeObjects(ExportMask.class, exportMaskUris, true);
            while (exportMaskIterator.hasNext()) {
                totalExportMaskObjectCount++;
                ExportMask exportMask = exportMaskIterator.next();
                if (exportMask != null && !NullColumnValueGetter.isNullURI(exportMask.getStorageDevice())) {
                    logger.info("Processing mask {}", exportMask.forDisplay());
                    URI systemUri = exportMask.getStorageDevice();
                    String systemType = systemTypeMap.get(systemUri);
                    if (systemType == null) {
                        system = dbClient.queryObject(StorageSystem.class, systemUri);
                        if (system != null) {
                            systemTypeMap.put(systemUri, system.getSystemType());
                            systemType = system.getSystemType();
                        }
                    }

                    if (systemType != null && (Type.vmax.toString().equalsIgnoreCase(systemType) ||
                            (Type.vplex.toString().equalsIgnoreCase(systemType)))) {

                        logger.info("Processing existing initiators for export mask {} on {} storage {}", exportMask.getId(),
                                systemType, systemUri);
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
                            logger.info("Processed existing initiators for export mask {} on {} storage {} and updated the Mask Object",
                                    exportMask.getId(), systemType, systemUri);
                            dbClient.updateObject(exportMask);
                            exportMaskUpdatedCount++;
                        }
                    } else if (systemType == null) {
                        logger.error("could not determine storage system type for exportMask {}",
                                exportMask.forDisplay());
                    }
                }
            }
            logger.info("Updated Existing information on {} of {} Export Mask Objects on VMAX Storage",
                    exportMaskUpdatedCount, totalExportMaskObjectCount);
        } catch (Exception e) {
            logger.error("Fail to migrate ExportMask existingInitiator migration into userCreatedInitiator", e);
        }
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
            if (maskResource.startsWith("urn:storageos:Host")) {
                // We found scenarios where VPLEX Initiators/ports do not have the Host Name set and this is handled below.
                if (!NullColumnValueGetter.isNullURI(existingInitiator.getHost())) {
                    differentResource = !maskResource.equals(existingInitiator.getHost().toString());
                } else {
                    differentResource = true;
                }
            } else {
                differentResource = !maskResource.equals(existingInitiator.getClusterName());
            }
        }
        return differentResource;
    }

}
