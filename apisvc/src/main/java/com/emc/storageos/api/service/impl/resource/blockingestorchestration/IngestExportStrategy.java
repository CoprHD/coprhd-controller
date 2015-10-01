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

import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.model.block.VolumeExportIngestParam;
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
    public <T extends BlockObject> T ingestExportMasks(UnManagedVolume unManagedVolume, VolumeExportIngestParam exportIngestParam,
            ExportGroup exportGroup, T blockObject,
            List<UnManagedVolume> unManagedVolumesToBeDeleted, StorageSystem system, boolean exportGroupCreated, List<Initiator> deviceInitiators) throws IngestionException {

        if (null != exportGroup) {
            if (null != unManagedVolume.getUnmanagedExportMasks() && !unManagedVolume.getUnmanagedExportMasks().isEmpty()) {
                List<URI> unManagedMaskUris = new ArrayList<URI>(Collections2.transform(
                        unManagedVolume.getUnmanagedExportMasks(), CommonTransformerFunctions.FCTN_STRING_TO_URI));
                List<UnManagedExportMask> unManagedMasks = _dbClient.queryObject(UnManagedExportMask.class, unManagedMaskUris);
                int originalSize = unManagedMasks.size();
                MutableInt masksIngestedCount = new MutableInt(0);
                // Ingest Associated Masks
                ingestExportOrchestrator.ingestExportMasks(unManagedVolume, unManagedMasks, exportIngestParam, exportGroup, blockObject,
                        system, exportGroupCreated, masksIngestedCount, deviceInitiators);
                // If the internal flags are set, return the block object
                if (blockObject.checkInternalFlags(Flag.NO_PUBLIC_ACCESS)) {
                    // check if none of the export masks are ingested
                    if (masksIngestedCount.intValue() == 0) {
                        throw IngestionException.exceptions.unmanagedVolumeMasksNotIngested(unManagedVolume.getLabel());
                    } else {
                        return blockObject;
                    }
                }
                if (unManagedVolume.getUnmanagedExportMasks().size() != originalSize) {
                    // delete this volume only if the masks are ingested.
                    if (VolumeIngestionUtil.canDeleteUnManagedVolume(unManagedVolume)) {
                        _logger.info("Marking UnManaged Volume  {} inactive as it doesn't have any associated unmanaged export masks ",
                                unManagedVolume.getNativeGuid());
                        unManagedVolume.setInactive(true);
                        unManagedVolumesToBeDeleted.add(unManagedVolume);
                    }

                    return blockObject;
                } else {
                    throw IngestionException.exceptions.unmanagedVolumeMasksNotIngested(unManagedVolume.getLabel());
                }
            }
        }

        return blockObject;

    }

}
