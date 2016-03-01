/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedProtectionSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;

public class IngestExportStrategy {
    private static final Logger _logger = LoggerFactory.getLogger(IngestStrategy.class);

    private DbClient _dbClient;
    private BlockIngestExportOrchestrator ingestExportOrchestrator;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setIngestExportOrchestrator(BlockIngestExportOrchestrator ingestExportOrchestrator) {
        this.ingestExportOrchestrator = ingestExportOrchestrator;
    }

    /**
     * After volume object gets created successfully locally, now start
     * running ingest associated masks of the volume
     */
    public <T extends BlockObject> T ingestExportMasks(UnManagedVolume unManagedVolume,
            T blockObject, IngestionRequestContext requestContext) throws IngestionException {

        if (null != requestContext.getExportGroup()) {

            // refresh ExportGroup
            URI exportGroupUri = requestContext.getExportGroup().getId();
            if (exportGroupUri != null) {
                ExportGroup existingGroup = _dbClient.queryObject(ExportGroup.class, exportGroupUri);
                if (null != existingGroup) {
                    requestContext.setExportGroup(existingGroup);
                    requestContext.setExportGroupCreated(false);
                }
            }

            if (null != unManagedVolume.getUnmanagedExportMasks() && !unManagedVolume.getUnmanagedExportMasks().isEmpty()) {
                List<URI> unManagedMaskUris = new ArrayList<URI>(Collections2.transform(
                        unManagedVolume.getUnmanagedExportMasks(), CommonTransformerFunctions.FCTN_STRING_TO_URI));
                List<UnManagedExportMask> unManagedMasks = _dbClient.queryObject(UnManagedExportMask.class, unManagedMaskUris);
                int originalSize = unManagedMasks.size();
                MutableInt masksIngestedCount = new MutableInt(0);

                // Ingest Associated Masks
                ingestExportOrchestrator.ingestExportMasks(
                        requestContext, unManagedVolume, blockObject, unManagedMasks, masksIngestedCount);

                List<String> errorMessages = requestContext.getErrorMessagesForVolume(unManagedVolume.getNativeGuid());

                // If the internal flags are set, return the block object
                if (blockObject.checkInternalFlags(Flag.PARTIALLY_INGESTED)) {
                    // check if none of the export masks are ingested
                    if (masksIngestedCount.intValue() == 0) {
                        if (null != errorMessages && !errorMessages.isEmpty()) {
                            throw IngestionException.exceptions.unmanagedVolumeMasksNotIngestedAdditionalInfo(
                                    unManagedVolume.getLabel(), Joiner.on(", ").join(errorMessages));
                        } else {
                            throw IngestionException.exceptions.unmanagedVolumeMasksNotIngested(
                                    unManagedVolume.getLabel());
                        }
                    } else {
                        return blockObject;
                    }
                }
                if (unManagedVolume.getUnmanagedExportMasks().size() != originalSize) {
                    // delete this volume only if the masks are ingested.
                    if (VolumeIngestionUtil.canDeleteUnManagedVolume(unManagedVolume)) {
                        _logger.info("Marking UnManaged Volume {} inactive as it doesn't have any associated unmanaged export masks ",
                                unManagedVolume.getNativeGuid());

                        boolean isRPVolume = VolumeIngestionUtil.checkUnManagedResourceIsRecoverPointEnabled(unManagedVolume);
                        // if its RP volume and non RP exported, then check whether the RP CG is fully ingested
                        if (isRPVolume && VolumeIngestionUtil.checkUnManagedResourceIsNonRPExported(unManagedVolume)) {
                            List<DataObject> updateObjects = requestContext.getDataObjectsToBeUpdatedMap().get(unManagedVolume.getNativeGuid());
                            if (updateObjects == null) {
                                updateObjects = new ArrayList<DataObject>();
                                requestContext.getDataObjectsToBeUpdatedMap().put(unManagedVolume.getNativeGuid(), updateObjects);
                            }
                            List<UnManagedVolume> ingestedUnManagedVolumes = requestContext.findAllUnManagedVolumesToBeDeleted();
                            ingestedUnManagedVolumes.add(unManagedVolume);
                            UnManagedProtectionSet umpset = VolumeIngestionUtil.getUnManagedProtectionSetForUnManagedVolume(requestContext,
                                    unManagedVolume, _dbClient);
                            // If we are not able to find the unmanaged protection set from the unmanaged volume, it means that the
                            // unmanaged volume has already been ingested. In this case, try to get it from the managed volume
                            if (umpset == null) {
                                umpset = VolumeIngestionUtil.getUnManagedProtectionSetForManagedVolume(requestContext, blockObject,
                                        _dbClient);
                            }
                            // If fully ingested, then setup the RP CG too.
                            if (VolumeIngestionUtil.validateAllVolumesInCGIngested(ingestedUnManagedVolumes, umpset, _dbClient)) {
                                VolumeIngestionUtil.setupRPCG(requestContext, umpset, updateObjects, _dbClient);
                            } else { // else mark the volume as internal. This will be marked visible when the RP CG is ingested
                                blockObject.addInternalFlags(BlockRecoverPointIngestOrchestrator.INTERNAL_VOLUME_FLAGS);
                            }
                        }

                        unManagedVolume.setInactive(true);
                        requestContext.getUnManagedVolumesToBeDeleted().add(unManagedVolume);
                    }

                    return blockObject;
                } else {
                    if (null != errorMessages && !errorMessages.isEmpty()) {
                        throw IngestionException.exceptions.unmanagedVolumeMasksNotIngestedAdditionalInfo(
                                unManagedVolume.getLabel(), Joiner.on(", ").join(errorMessages));
                    } else {
                        throw IngestionException.exceptions.unmanagedVolumeMasksNotIngested(
                                unManagedVolume.getLabel());
                    }
                }
            }
        }

        return blockObject;
    }
}
