/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.volumecontroller.impl.smis.MetaVolumeRecommendation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

public class MetaVolumeUtils {

    private static final Logger _log = LoggerFactory.getLogger(MetaVolumeUtils.class);

    private static final int BYTESCONVERTER = 1024;
    private static final int KB_TO_GB_CONVERTER = 1024 * 1024;
    private static final int GB_32kb = 32 * KB_TO_GB_CONVERTER;  // 32 GB in KB
    private static final int GB_500kb = 500 * KB_TO_GB_CONVERTER;  // 500 GB in KB
    private static final int GB_1024kb = 1024 * KB_TO_GB_CONVERTER; // 1024 GB in KB
    private static final int GB_240kb = 240 * KB_TO_GB_CONVERTER;   // 240 GB in KB

    public static void prepareMetaVolumes(List<Volume> volumes, long metaMemberSize, long metaMemberCount, String metaType,
            DbClient dbClient) {
        // Set meta volume data in all volumes
        for (Volume volume : volumes) {
            prepareMetaVolume(volume, metaMemberSize, metaMemberCount, metaType, dbClient);
        }
    }

    public static void prepareMetaVolume(Volume volume, long metaMemberSize, long metaMemberCount, String metaType, DbClient dbClient) {
        // Set meta volume data in the volume
        volume.setIsComposite(true);
        volume.setCompositionType(metaType);
        volume.setMetaMemberSize(metaMemberSize);
        volume.setMetaMemberCount((int) metaMemberCount);
        volume.setTotalMetaMemberCapacity(metaMemberSize * metaMemberCount);

        dbClient.persistObject(volume);
    }

    /**
     * Helper method to return if a given storage volume should be created as meta volume.
     * 
     * @param volumeURI
     * @param dbClient
     * @return true/false
     */
    public static boolean createAsMetaVolume(URI volumeURI, DbClient dbClient, VirtualPoolCapabilityValuesWrapper capabilities) {

        boolean createAsMetaVolume = false;
        // Check if volumes have to be created as meta volumes
        Volume volume = dbClient.queryObject(Volume.class, volumeURI);
        VirtualPool vPool = dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, volume.getStorageController());
        StoragePool storagePool = dbClient.queryObject(StoragePool.class, volume.getPool());
        MetaVolumeRecommendation recommendation = MetaVolumeUtils.getCreateRecommendation(storageSystem, storagePool,
                volume.getCapacity(), volume.getThinlyProvisioned(), vPool.getFastExpansion(), capabilities);
        if (recommendation.isCreateMetaVolumes()) {
            createAsMetaVolume = true;
        }
        return createAsMetaVolume;
    }

    /**
     * Returns recommendation about use of meta volumes.
     * The recommendation includes if meta volumes should be used, meta member count, meta member capacity and meta volume type.
     * 
     * @param storageSystem storage system
     * @param storagePool storage pool in which volume should be created
     * @param capacity required size of volume
     * @param isThinlyProvisioned
     * @param fastExpansion if fast volume expansion is required
     * @return recommendation
     */
    public static MetaVolumeRecommendation getCreateRecommendation(StorageSystem storageSystem, StoragePool storagePool, long capacity,
            boolean isThinlyProvisioned,
            boolean fastExpansion, VirtualPoolCapabilityValuesWrapper capabilities) {

        _log.info(String.format(
                "Create recommendation for use of meta volumes: Storage type: %s,  \n   capacity: %s, isThinlyProvisioned: %s, " +
                        "fastExpansion: %s ", storageSystem.getSystemType(), capacity, isThinlyProvisioned, fastExpansion));

        MetaVolumeRecommendation recommendation = new MetaVolumeRecommendation();
        // When capabilities indicate that a volume is meta volume, create recommendation based on meta volume data in capabilities.
        // This is used to create srdf target with meta structure identical to the srdf source volume.
        if (capabilities != null && capabilities.getIsMetaVolume()) {
            recommendation.setCreateMetaVolumes(true);
            if (capabilities.getMetaVolumeType().equalsIgnoreCase(Volume.CompositionType.CONCATENATED.toString())) {
                recommendation.setMetaVolumeType(Volume.CompositionType.CONCATENATED);
            } else if (capabilities.getMetaVolumeType().equalsIgnoreCase(Volume.CompositionType.STRIPED.toString())) {
                recommendation.setMetaVolumeType(Volume.CompositionType.STRIPED);
            }
            recommendation.setMetaMemberSize(capabilities.getMetaVolumeMemberSize());
            recommendation.setMetaMemberCount(capabilities.getMetaVolumeMemberCount());
            _log.info(String
                    .format("Volume Create Recommendation (based on capabilities data): Use meta volumes: %s, Member Count: %s, Member size: %s, Meta type: %s",
                            recommendation.isCreateMetaVolumes(), recommendation.getMetaMemberCount(),
                            recommendation.getMetaMemberSize(), recommendation.getMetaVolumeType()));
            return recommendation;
        }
        if (null == storagePool.getPoolClassName()
                || storagePool.getPoolClassName().equalsIgnoreCase(StoragePool.PoolClassNames.Symm_SRPStoragePool.toString())
                || storagePool.getPoolClassName().equalsIgnoreCase(StoragePool.PoolClassNames.IBMTSDS_VirtualPool.toString())
                || storagePool.getPoolClassName().equalsIgnoreCase(StoragePool.PoolClassNames.Clar_UnifiedStoragePool.toString())
                || storagePool.getPoolClassName().equalsIgnoreCase(StoragePool.PoolClassNames.VNXe_Pool.name())) {
            // If the pool class name is not set, then it's not necessary to do MetaVolumes
            // meta volumes are not supported for clarion volumes created in Unified pools, and not supported in VNXe either.
            recommendation.setCreateMetaVolumes(false);
            _log.info(String.format("Volume Create Recommendation: Use meta volumes: %s",
                    recommendation.isCreateMetaVolumes()));
            return recommendation;
        }

        // On VMAX arrays only concatenated meta volumes can be expanded fast.
        // If fast expansion is required use concatenated, otherwise use striped.
        // On VNX we always use striped. No penalty for striped expansion on VNX (is this true?).
        Volume.CompositionType metaVolumeType;
        if (fastExpansion && storageSystem.getSystemType().equals(StorageSystem.Type.vmax.toString())) {
            metaVolumeType = Volume.CompositionType.CONCATENATED;
        } else {
            metaVolumeType = Volume.CompositionType.STRIPED;
        }

        _log.info(String.format(
                "Regular volume capacity limits for storage pool %s,  %n   max thin volume capacity: %s, max thick volume capacity: %s ",
                storagePool.getId(), storagePool.getMaximumThinVolumeSize(), storagePool.getMaximumThickVolumeSize()));

        // Get storage pool setting for maximum supported regular volume size.
        // Typically this size is 240GB, but it can be different on VMAX if auto meta configuration is enabled and minimum meta volume size
        // is defined there
        // less than 240GB.
        long regularVolumeSizeLimitKb = isThinlyProvisioned ?
                storagePool.getMaximumThinVolumeSize() : storagePool.getMaximumThickVolumeSize();
        _log.info(String.format("Regular volume size limit for storage pool: %s KB", regularVolumeSizeLimitKb));

        long capacityKb = (capacity % BYTESCONVERTER == 0) ? capacity / BYTESCONVERTER : capacity / BYTESCONVERTER + 1;
        // TODO temporary. remove later
        // regularVolumeSizeLimitKb = 1048576 + 10; // 1GB + 10 kb

        // Check if we need to use meta volumes.
        // Use meta volumes when capacity is large than maximum regular volume size limit.
        // This is a common case.
        boolean useMetaVolumes = false;
        long metaMemberCount = 1;
        if (capacityKb >= regularVolumeSizeLimitKb) {
            useMetaVolumes = true;

            // Calculate meta member count
            metaMemberCount = (capacityKb % regularVolumeSizeLimitKb == 0) ?
                    capacityKb / regularVolumeSizeLimitKb : capacityKb / regularVolumeSizeLimitKb + 1;
        }

        // For VMAX striped meta volumes, member count calculation is done differently
        // For thin striped volumes use 8 way meta volumes starting from 240GB/regularVolumeSizeLimit volumes size and up
        // to max possible limit available with 8 members.
        // SAP asked to use 16 and 32 members for thin striped volumes after we exhausted capacity with 8 members.
        // I do not see any issues with this and I added this support.
        if (storageSystem.getSystemType().equals(StorageSystem.Type.vmax.toString()) &&
                metaVolumeType == Volume.CompositionType.STRIPED) {

            long maxLimitWith16Members = 16 * regularVolumeSizeLimitKb;
            long maxLimitWith32Members = 32 * regularVolumeSizeLimitKb;
            if (isThinlyProvisioned) { // create 8-way meta up to max possible size
                if (capacityKb >= regularVolumeSizeLimitKb && capacityKb < 8 * regularVolumeSizeLimitKb) {
                    metaMemberCount = 8;
                    useMetaVolumes = true;
                } else if (capacityKb >= 8 * regularVolumeSizeLimitKb && capacityKb < maxLimitWith16Members) {
                    metaMemberCount = 16;
                    useMetaVolumes = true;
                } else if (capacityKb >= maxLimitWith16Members && capacityKb < maxLimitWith32Members) {
                    metaMemberCount = 32;
                    useMetaVolumes = true;
                }
            } else {  // thick striped meta, use the following rules
                // Current rules are to use 4 way meta for volumes between 32GB and 500GB, 8 way meta for volumes between 500GB and 1024GB,
                // 16 way meta for volumes from 1024 and up. After that increase meta members count as needed according to max. regular
                // volume size limit.
                // On VMAX with auto meta configuration enabled, regular volume size limit can be small and we need to account this to make
                // sure we do not create
                // members bigger than auto meta limit.
                // SAP asked to continue with 32 members after we exhausted 16 member capacity. I do not see any issues with
                // this and I added this support.
                long minimumMetaSizeLimit = (GB_32kb < regularVolumeSizeLimitKb) ? GB_32kb : regularVolumeSizeLimitKb;
                long maxLimitWith4Members = (GB_500kb < 4 * regularVolumeSizeLimitKb) ? GB_500kb : 4 * regularVolumeSizeLimitKb;
                long maxLimitWith8Members = (GB_1024kb < 8 * regularVolumeSizeLimitKb) ? GB_1024kb : 8 * regularVolumeSizeLimitKb;
                if (capacityKb >= minimumMetaSizeLimit && capacityKb < maxLimitWith4Members) {
                    metaMemberCount = 4;
                    useMetaVolumes = true;
                } else if (capacityKb >= maxLimitWith4Members && capacityKb < maxLimitWith8Members) {
                    metaMemberCount = 8;
                    useMetaVolumes = true;
                } else if (capacityKb >= maxLimitWith8Members && capacityKb < maxLimitWith16Members) {
                    metaMemberCount = 16;
                    useMetaVolumes = true;
                } else if (capacityKb >= maxLimitWith16Members && capacityKb < maxLimitWith32Members) {
                    metaMemberCount = 32;
                    useMetaVolumes = true;
                }
            }
        }

        if (useMetaVolumes) {
            long metaMemberSize = (capacity % metaMemberCount == 0) ?
                    capacity / metaMemberCount : capacity / metaMemberCount + 1;

            recommendation.setCreateMetaVolumes(true);
            recommendation.setMetaVolumeType(metaVolumeType);
            recommendation.setMetaMemberSize(metaMemberSize);
            recommendation.setMetaMemberCount(metaMemberCount);
            _log.info(String.format("Volume Create Recommendation: Use meta volumes: %s, Member Count: %s, Member size: %s, Meta type: %s",
                    recommendation.isCreateMetaVolumes(), recommendation.getMetaMemberCount(),
                    recommendation.getMetaMemberSize(), recommendation.getMetaVolumeType()));
        } else {
            recommendation.setCreateMetaVolumes(false);
            _log.info(String.format("Volume Create Recommendation: Use meta volumes: %s", recommendation.isCreateMetaVolumes()));
        }

        return recommendation;
    }

    /**
     * Returns recommendation about volume extension.
     * The recommendation includes if meta volumes should be used, meta member count, meta member capacity and meta volume type.
     * 
     * @param storageSystem storage system
     * @param storagePool storage pool in which volume should be created
     * @param capacity current size
     * @param newCapacity new required size
     * @param metaMemberSize size of meta members used for expansion
     * @param fastExpansion if fast volume expansion is required
     * @return recommendation
     */
    public static MetaVolumeRecommendation getExpandRecommendation(StorageSystem storageSystem, StoragePool storagePool, long capacity,
            long newCapacity, long metaMemberSize, boolean isThinlyProvisioned,
            boolean fastExpansion) {

        _log.info(String
                .format(
                        "Create recommendation for volume expansion: Storage type: %s,  \n   capacity: %s, new capacity: %s, \n  isThinlyProvisioned: %s, "
                                +
                                "metaMemeberSize: %s, fastExpansion: %s ", storageSystem.getSystemType(), capacity, newCapacity,
                        isThinlyProvisioned,
                        metaMemberSize, fastExpansion));

        MetaVolumeRecommendation recommendation = new MetaVolumeRecommendation();

        if (storageSystem.checkIfVmax3()) {
            recommendation.setCreateMetaVolumes(false);
            _log.info(String.format("Volume Expand Recommendation (VMAX3): Use meta volumes: %s", recommendation.isCreateMetaVolumes()));
            return recommendation;
        }

        if (storageSystem.getSystemType().equals(StorageSystem.Type.xtremio.name())) {
            recommendation.setCreateMetaVolumes(false);
            _log.info(String.format("Volume Expand Recommendation: Use meta volumes: %s", recommendation.isCreateMetaVolumes()));
            return recommendation;
        }

        // For Clarion Unified pool and VNXe pool extension can be done only as regular volume
        if (null != storagePool.getPoolClassName()
                && (storagePool.getPoolClassName().equalsIgnoreCase(StoragePool.PoolClassNames.Clar_UnifiedStoragePool.toString())
                || storagePool.getPoolClassName().equalsIgnoreCase(StoragePool.PoolClassNames.VNXe_Pool.toString()))) {
            recommendation.setCreateMetaVolumes(false);
            _log.info(String.format("Volume Expand Recommendation: Use meta volumes: %s", recommendation.isCreateMetaVolumes()));
            return recommendation;
        }

        // For Hitachi thin volume expansion can be done as regular volume
        if (isThinlyProvisioned && storageSystem.getSystemType().equals(StorageSystem.Type.hds.name())) {
            recommendation.setCreateMetaVolumes(false);
            _log.info(String.format("Hitachi Volume Expand Recommendation: Use meta volumes: %s", recommendation.isCreateMetaVolumes()));
            return recommendation;
        }

        // For Openstack, volume expansion can be done as regular volume
        if (StorageSystem.Type.openstack.name().equalsIgnoreCase(storageSystem.getSystemType())) {
            recommendation.setCreateMetaVolumes(false);
            _log.info(String.format("Openstack Volume Expand Recommendation: Use meta volumes: %s", recommendation.isCreateMetaVolumes()));
            return recommendation;
        }

        if (storageSystem.getSystemType().equals(StorageSystem.Type.scaleio.name())) {
            recommendation.setCreateMetaVolumes(false);
            _log.info(String.format("ScaleIO Volume Expand Recommendation: Use meta volumes: %s", recommendation.isCreateMetaVolumes()));
            return recommendation;
        }

        // For IBM XIV, volume expansion can be done as regular volume
        if (StorageSystem.Type.ibmxiv.name().equalsIgnoreCase(storageSystem.getSystemType())) {
            recommendation.setCreateMetaVolumes(false);
            _log.info(String.format("IBM XIV Volume Expand Recommendation: Use meta volumes: %s", recommendation.isCreateMetaVolumes()));
            return recommendation;
        }

        if (storageSystem.getSystemType().equals(StorageSystem.Type.ceph.name())) {
            recommendation.setCreateMetaVolumes(false);
            _log.info(String.format("Ceph Volume Expand Recommendation: Use meta volumes: %s", recommendation.isCreateMetaVolumes()));
            return recommendation;
        }

        // For all other volume types we have to use meta volumes for extension
        recommendation.setCreateMetaVolumes(true);
        // Set meta volume type.
        // On VMAX arrays only concatenated meta volumes can be expanded fast
        // Use concatenated volumes for fast expansion on VMAX. When fast expansion is not requested, use striped type on VMAX.
        if (fastExpansion && storageSystem.getSystemType().equals(StorageSystem.Type.vmax.toString())) {
            recommendation.setMetaVolumeType(Volume.CompositionType.CONCATENATED);
        } else {
            recommendation.setMetaVolumeType(Volume.CompositionType.STRIPED);
        }

        // Based on performance recommendations: On VNX use striped for thick meta volume and concatenated for thin meta volumes.
        // On VNX only thick volumes (concrete pool volumes) can be meta volumes.
        // All types of VNX volumes can be expanded fast (is this true?).
        if (storageSystem.getSystemType().equals(StorageSystem.Type.vnxblock.toString())) {
            Volume.CompositionType metaType = isThinlyProvisioned ? Volume.CompositionType.CONCATENATED : Volume.CompositionType.STRIPED;
            recommendation.setMetaVolumeType(metaType);
        }

        // For Hitachi, all thick volumes are expanded as meta and they are concatenated.
        // As per the Hitachi documentation, if a volume is exported then the data is safe when we expand, else data will be lost.
        if (StorageSystem.Type.hds.name().equals(storageSystem.getSystemType()) && !isThinlyProvisioned) {
            recommendation.setMetaVolumeType(Volume.CompositionType.CONCATENATED);
        }

        // Check if capacity bigger than newCapacity. In this case no expansion is required.
        if (capacity >= newCapacity) {
            recommendation.setMetaMemberSize(0);
            recommendation.setMetaMemberCount(0);
        } else {
            // Calculate number of meta members for extension
            long extensionSize = newCapacity - capacity;
            long metaMemberCount = (extensionSize % metaMemberSize == 0) ?
                    extensionSize / metaMemberSize : extensionSize / metaMemberSize + 1;
            recommendation.setMetaMemberSize(metaMemberSize);
            recommendation.setMetaMemberCount(metaMemberCount);
        }

        _log.info(String.format("Volume Expand Recommendation: Use meta volumes: %s, Member Count: %s, Member size: %s, Meta type: %s",
                recommendation.isCreateMetaVolumes(), recommendation.getMetaMemberCount(),
                recommendation.getMetaMemberSize(), recommendation.getMetaVolumeType()));
        return recommendation;
    }

}
