/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.smis.MetaVolumeRecommendation;
import com.emc.storageos.volumecontroller.impl.utils.MetaVolumeUtils;

public class VmaxCapacityCalculator implements CapacityCalculator {
    private static final Logger logger = LoggerFactory.getLogger(VmaxCapacityCalculator.class);

    private static final long tracksPerCylinder = 15;
    private static final long blocksPerTrack = 128;
    private static final long blocksPerTrackVMAX3 = 256;
    private static final long bytesPerBlock = 512;

    /**
     * {@inheritDoc}
     */
    @Override
    public Long calculateAllocatedCapacity(Long requestedCapacity, Volume volume, DbClient dbClient) {
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, volume.getStorageController());

        if (requestedCapacity != null) {
            long bytesPerCylinder = 0L;
            if (storageSystem != null &&
                    storageSystem.checkIfVmax3()) {
                bytesPerCylinder = (tracksPerCylinder * blocksPerTrackVMAX3 * bytesPerBlock);
            } else {
                bytesPerCylinder = (tracksPerCylinder * blocksPerTrack * bytesPerBlock);

                VirtualPool vPool = dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
                StoragePool storagePool = dbClient.queryObject(StoragePool.class, volume.getPool());
                // Determine if we are provisioning meta volumes. Meta volume provisioning only applies to non-VMAX3.
                MetaVolumeRecommendation metaRecommendation = MetaVolumeUtils.getCreateRecommendation(storageSystem, storagePool,
                        requestedCapacity, volume.getThinlyProvisioned(), Volume.determineFastExpansionForVolume(volume, dbClient), null);

                if (metaRecommendation.isCreateMetaVolumes()) {
                    long metaCount = metaRecommendation.getMetaMemberCount();
                    long metaSize = metaRecommendation.getMetaMemberSize();
                    long metaCylinderCount = getCylinderCount(metaSize, bytesPerCylinder);
                    long allocatedSingleMetaCapacity = (metaCylinderCount * bytesPerCylinder);

                    // Return the total meta allocation size
                    long allocatedMetaCapacity = (metaCount * allocatedSingleMetaCapacity);

                    logger.info(String
                            .format(
                                    "Determined that volume %s is being provisioned as meta volumes.  Allocated capacity per meta will be %d. Total volume allocation (%d metas) will be %d.",
                                    volume.getLabel(), allocatedSingleMetaCapacity, metaCount, allocatedMetaCapacity));

                    return allocatedMetaCapacity;
                }
            }

            long cyls = getCylinderCount(requestedCapacity, bytesPerCylinder);
            return (cyls * bytesPerCylinder);
        }

        return requestedCapacity;
    }

    /**
     * Computes the provisioned cylinder count for a given requested capacity size
     * and bytes-per-cylinder value.
     *
     * @param requestedCapacity the requested capacity size
     * @param bytesPerCylinder the bytes per cylinder
     * @return the cylinder count
     */
    private long getCylinderCount(long requestedCapacity, long bytesPerCylinder) {
        return (long) Math.ceil((double) requestedCapacity / bytesPerCylinder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean capacitiesCanMatch(String storageSystemType) {
        if (storageSystemType.equalsIgnoreCase(DiscoveredDataObject.Type.xtremio.name())) {
            return false;
        }
        return true;
    }
}
