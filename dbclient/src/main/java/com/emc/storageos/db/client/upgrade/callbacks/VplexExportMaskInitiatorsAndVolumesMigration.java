/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration process to handle setting of _userAddedVolumes _userAddedInitiators and _initiators
 * in the VPlex ExportMasks
 * 
 * @author tahals
 * @since 2.0
 */
public class VplexExportMaskInitiatorsAndVolumesMigration extends BaseCustomMigrationCallback {

    private static final Logger log = LoggerFactory.getLogger(VplexExportMaskInitiatorsAndVolumesMigration.class);

    @Override
    public void process() throws MigrationCallbackException {

        DbClient dbClient = getDbClient();

        try {

            List<URI> exportMaskUris = dbClient.queryByType(ExportMask.class, true);
            Iterator<ExportMask> exportMasks = dbClient.queryIterativeObjects(ExportMask.class, exportMaskUris, true);

            while (exportMasks.hasNext()) {
                ExportMask exportMask = exportMasks.next();
                try {
                    URI storageSytsemURI = exportMask.getStorageDevice();
                    StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storageSytsemURI);
                    // We only need to update VPlex ExportMask
                    if ((storageSystem != null)
                            && (DiscoveredDataObject.Type.vplex.name().equals(storageSystem
                                    .getSystemType()))) {
                        // Volumes list can be inconsistent only when its created by ViPR
                        if (exportMask.getCreatedBySystem()) {
                            log.info("Looking at export mask " + exportMask.getMaskName() + " Export Mask ID is :" + exportMask.getId()
                                    + "created by system is " + exportMask.getCreatedBySystem());
                            StringMap volumeMaps = exportMask.getVolumes();
                            if (volumeMaps != null && !volumeMaps.isEmpty()) {
                                Set<String> volumeIds = volumeMaps.keySet();
                                List<BlockObject> volumes = new ArrayList<BlockObject>();
                                for (String volumeId : volumeIds) {
                                    BlockObject bo = BlockObject.fetch(dbClient, URI.create(volumeId));
                                    if (bo != null) {
                                        if (bo.getWWN() == null) {
                                            log.info("Skipping volume " + bo.getId() + " " + bo.getLabel() + " as its wwn is null.");
                                            ;
                                        } else {
                                            volumes.add(bo);
                                        }
                                    }
                                }
                                StringMap userAddedVolumesMap = exportMask.getUserAddedVolumes();

                                if (userAddedVolumesMap == null && !volumes.isEmpty()) {
                                    // If there is nothing in the userAddedVolumesMap then add all the
                                    // volumes from the EXportMask Volumes list
                                    log.info("Adding volumes to the userCreatedVolumes " + volumes + "to the export mask "
                                            + exportMask.getMaskName() +
                                            " export mask ID is :" + exportMask.getId());
                                    try {
                                        exportMask.addToUserCreatedVolumes(volumes);
                                    } catch (Exception volEx) {
                                        log.error("Exception occured while adding volumes to the userCreatedVolumes");
                                        log.error(volEx.getMessage(), volEx);
                                    }
                                } else {
                                    // Iterate through all the volumes and add those volumes which are not in the
                                    // user added list
                                    for (BlockObject volume : volumes) {
                                        if (volume.getWWN() != null && !volume.getWWN().isEmpty()) {
                                            if (userAddedVolumesMap.get(BlockObject.normalizeWWN(volume.getWWN())) == null) {
                                                log.info("Adding volume to the userCreatedVolumes " + volume + "to the export mask "
                                                        + exportMask.getMaskName() +
                                                        "exportmask ID is :" + exportMask.getId());
                                                try {
                                                    exportMask.addToUserCreatedVolumes(volume);
                                                } catch (Exception volEx) {
                                                    log.error("Exception occured while adding volume to the userCreatedVolumes");
                                                    log.error(volEx.getMessage(), volEx);
                                                }
                                            }
                                        }
                                    }
                                }
                                // Add all the initiators from the initiators list if any to the userAddedInitiators
                                populateExportMaskUserAddedInitiators(exportMask);
                                dbClient.persistObject(exportMask);
                            }

                        } else {
                            log.info("Looking at export mask " + exportMask.getMaskName() + " Export Mask ID is :" + exportMask.getId()
                                    + "created by system is " + exportMask.getCreatedBySystem());

                            // First add all the initiators from the initiators list if any to the userAddedInitiators
                            populateExportMaskUserAddedInitiators(exportMask);

                            // initiators list can be inconsistent when we are reusing existing
                            // storage view in which case createdBySystem will be false
                            // Add existing initiators to the initiators list if we can find Initiator object for those pwwn
                            if (exportMask.getExistingInitiators() != null && !exportMask.getExistingInitiators().isEmpty()) {
                                StringSet existingInitiators = exportMask.getExistingInitiators();
                                List<URI> existingInitiatorsURIs = new ArrayList<URI>();
                                for (String pwwn : existingInitiators) {
                                    Initiator initiator = findInitiatorInDB(pwwn);
                                    if (initiator != null) {
                                        existingInitiatorsURIs.add(initiator.getId());
                                    }
                                }
                                if (exportMask.getInitiators() == null && !existingInitiatorsURIs.isEmpty()) {
                                    log.info("Adding existingInitiators to the initiators " + existingInitiatorsURIs
                                            + "to the export mask " + exportMask.getMaskName() +
                                            "export mask ID is :" + exportMask.getId());
                                    exportMask.setInitiators(StringSetUtil.uriListToStringSet(existingInitiatorsURIs));
                                } else {
                                    for (URI uri : existingInitiatorsURIs) {
                                        log.info("Adding existingInitiator to the initiators " + uri + "to the export mask "
                                                + exportMask.getMaskName() +
                                                "export mask ID is :" + exportMask.getId());
                                        exportMask.getInitiators().add(uri.toString());
                                    }
                                }
                            }
                            dbClient.persistObject(exportMask);
                        }
                    }
                } catch (Exception emex) {
                    log.error("Exception occured while migrating VPLEX ExportMask " + exportMask.getId() + " " + exportMask.getMaskName());
                    log.error(emex.getMessage(), emex);
                }
            }

        } catch (Exception ex) {
            log.error("Exception occured while migrating VPLEX ExportMask ");
            log.error(ex.getMessage(), ex);
        }
        log.info("Done VplexExportMaskInitiatorsAndVolumesMigration.");
    }

    /**
     * This method returns initiator if it exist in database
     * 
     * @param pwwn Initiators pwwn
     * @return initiator object or null
     * @throws IOException
     */
    private Initiator findInitiatorInDB(String pwwn) throws IOException {
        Initiator initiator = null;
        String portWWN = WWNUtility.getWWNWithColons(pwwn);
        log.info("Looking for initiator {} in database", portWWN);
        URIQueryResultList queryResults = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getInitiatorPortInitiatorConstraint(portWWN), queryResults);
        Iterator<URI> resultsIter = queryResults.iterator();
        if (resultsIter.hasNext()) {
            log.info("Found initiator {}", portWWN);
            initiator = dbClient.queryObject(Initiator.class, resultsIter.next());
        }
        return initiator;
    }

    /**
     * This method adds initiators if any to the userAddedInitiators in the
     * mentioned exportMask.
     * 
     * @param exportMask The exportMask
     * @return returns same exportMask by making changes if applicable
     */
    private ExportMask populateExportMaskUserAddedInitiators(ExportMask exportMask) {

        if (exportMask.getInitiators() != null && !exportMask.getInitiators().isEmpty()) {
            StringMap userAddedInitiatorsMap = exportMask.getUserAddedInitiators();
            List<Initiator> initiators = new ArrayList<Initiator>();
            for (String initiatorId : exportMask.getInitiators()) {
                Initiator initiator = dbClient.queryObject(Initiator.class, URI.create(initiatorId));
                if (initiator != null) {
                    initiators.add(initiator);
                }
            }
            // If there is nothing in the userAddedInitiatorsMap then add all the
            // initiators from the ExportMask initiators list to the user added initiators
            if (userAddedInitiatorsMap == null && !initiators.isEmpty()) {
                exportMask.addToUserCreatedInitiators(initiators);
                log.info("Adding initiators to the userCreatedInitiators " + initiators + "to the export mask " + exportMask.getMaskName() +
                        "export mask ID is :" + exportMask.getId());
            } else {
                // Iterate through all the initiators and add those initiators
                // which are not in the user added list
                for (Initiator initiator : initiators) {
                    if (userAddedInitiatorsMap.get(Initiator.normalizePort(initiator.getInitiatorPort())) == null) {
                        log.info("Adding initiator to the userCreatedInitiators" + initiator + "to the export mask "
                                + exportMask.getMaskName() +
                                "exportmask ID is :" + exportMask.getId());
                        exportMask.addToUserCreatedInitiators(initiator);
                    }
                }
            }
        }
        return exportMask;
    }

}
