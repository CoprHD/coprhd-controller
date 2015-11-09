/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestStrategyFactory.IngestStrategyEnum;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestStrategyFactory.ReplicationStrategy;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestStrategyFactory.VolumeType;
import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;

/**
 * Responsible for ingesting block local volumes.
 */
public class BlockVolumeIngestOrchestrator extends BlockIngestOrchestrator {

    private static final Logger _logger = LoggerFactory.getLogger(BlockVolumeIngestOrchestrator.class);

    // A reference to the ingest strategy factory.
    private IngestStrategyFactory ingestStrategyFactory;

    /**
     * Setter for the ingest strategy factory.
     * 
     * @param ingestStrategyFactory A reference to the ingest strategy factory.
     */
    public void setIngestStrategyFactory(IngestStrategyFactory ingestStrategyFactory) {
        this.ingestStrategyFactory = ingestStrategyFactory;
    }

    @Override
    protected <T extends BlockObject> T ingestBlockObjects(List<URI> systemCache, List<URI> poolCache, StorageSystem system,
            UnManagedVolume unManagedVolume,
            VirtualPool vPool, VirtualArray virtualArray, Project project, TenantOrg tenant,
            List<UnManagedVolume> unManagedVolumesSuccessfullyProcessed,
            Map<String, BlockObject> createdObjectMap, Map<String, List<DataObject>> updatedObjectMap, boolean unManagedVolumeExported,
            Class<T> clazz,
            Map<String, StringBuffer> taskStatusMap, String vplexIngestionMethod) throws IngestionException {

        Volume volume = null;

        URI unManagedVolumeUri = unManagedVolume.getId();
        String volumeNativeGuid = unManagedVolume.getNativeGuid().replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                VolumeIngestionUtil.VOLUME);

        volume = VolumeIngestionUtil.checkIfVolumeExistsInDB(volumeNativeGuid, _dbClient);
        // Check if ingested volume has exportmasks pending for ingestion.
        if (isExportIngestionPending(volume, unManagedVolumeUri, unManagedVolumeExported)) {
            return clazz.cast(volume);
        }

        if (null == volume) {
            validateUnManagedVolume(unManagedVolume, vPool);
            // @TODO Need to revisit this. In 8.x Provider, ReplicationGroup is automatically created when a volume is associated to a
            // StorageGroup.
            // checkUnManagedVolumeAddedToCG(unManagedVolume, virtualArray, tenant, project, vPool);
            checkVolumeExportState(unManagedVolume, unManagedVolumeExported);
            checkVPoolValidForExportInitiatorProtocols(vPool, unManagedVolume);
            checkHostIOLimits(vPool, unManagedVolume, unManagedVolumeExported);

            StoragePool pool = validateAndReturnStoragePoolInVAarray(unManagedVolume, virtualArray);

            // validate quota is exceeded for storage systems and pools
            checkSystemResourceLimitsExceeded(system, unManagedVolume, systemCache);
            checkPoolResourceLimitsExceeded(system, pool, unManagedVolume, poolCache);
            String autoTierPolicyId = getAutoTierPolicy(unManagedVolume, system, vPool);
            validateAutoTierPolicy(autoTierPolicyId, unManagedVolume, vPool);

            volume = createVolume(system, volumeNativeGuid, pool, virtualArray, vPool, unManagedVolume, project, tenant, autoTierPolicyId);
        }

        if (volume != null) {
            String syncActive = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.IS_SYNC_ACTIVE.toString(), unManagedVolume.getVolumeInformation());
            boolean isSyncActive = (null != syncActive) ? Boolean.parseBoolean(syncActive) : false;
            volume.setSyncActive(isSyncActive);

            if (VolumeIngestionUtil.isFullCopy(unManagedVolume)) {
                _logger.info("Setting clone related properties {}", unManagedVolume.getId());
                String replicaState = PropertySetterUtil.extractValueFromStringSet(
                        SupportedVolumeInformation.REPLICA_STATE.toString(), unManagedVolume.getVolumeInformation());
                volume.setReplicaState(replicaState);
            }
        }

        // Note that a snapshot target volume can also be a VPLEX backend volume.
        // When the VPLEX ingest orchestrator is executed, it gets the ingestion
        // strategy for the backend volume and executes it. If the backend volume
        // is also a snapshot target volume, then this snap ingestion strategy is
        // invoked and a BlockSnapshot instance will result. That is fine because
        // we still need to represent that snapshot target volume as a BlockSnapshot
        // instance. However, we also need a Volume instance to represent the VPLEX
        // backend volume. Therefore, if the snapshot target volume is also a
        // VPLEX backend volume, we get the local volume ingest strategy, which is
        // the ingestion strategy invoked for a backend volume when it is not a
        // snapshot to create this Volume instance, and we add it to the created
        // object list. Note that since the Volume is added to the created
        // objects list and the Volume and BlockSnapshot instance will have the
        // same native GUID, we can't add this snapshot into the created objects
        // list when invoked out of the VPLEX ingest strategy because it will replace
        // the Volume and only the snapshot would get created.
        BlockObject snap = null;
        if (VolumeIngestionUtil.isSnapshot(unManagedVolume)) {
            String strategyKey = ReplicationStrategy.LOCAL.name() + "_" + VolumeType.SNAPSHOT.name();
            IngestStrategy ingestStrategy = ingestStrategyFactory.getIngestStrategy(IngestStrategyEnum.getIngestStrategy(strategyKey));
            snap = ingestStrategy.ingestBlockObjects(systemCache, poolCache,
                    system, unManagedVolume, vPool, virtualArray,
                    project, tenant, unManagedVolumesSuccessfullyProcessed, createdObjectMap,
                    updatedObjectMap, true, BlockSnapshot.class, taskStatusMap, vplexIngestionMethod);
            createdObjectMap.put(snap.getNativeGuid(), snap);
        }

        // Run this always when volume NO_PUBLIC_ACCESS
        if (markUnManagedVolumeInactive(unManagedVolume, volume,
                unManagedVolumesSuccessfullyProcessed, createdObjectMap, updatedObjectMap,
                taskStatusMap, vplexIngestionMethod)) {
            _logger.info("All the related replicas and parent has been ingested ",
                    unManagedVolume.getNativeGuid());
            // mark inactive if this is not to be exported. Else, mark as
            // inactive after successful export
            if (!unManagedVolumeExported) {
                unManagedVolume.setInactive(true);
                unManagedVolumesSuccessfullyProcessed.add(unManagedVolume);
            }
        } else if (volume != null) {
            _logger.info(
                    "Not all the parent/replicas of unManagedVolume {} have been ingested , hence marking as internal",
                    unManagedVolume.getNativeGuid());
            volume.addInternalFlags(INTERNAL_VOLUME_FLAGS);
        }

        return clazz.cast(volume);
    }

    @Override
    protected void validateAutoTierPolicy(String autoTierPolicyId, UnManagedVolume unManagedVolume, VirtualPool vPool) {
        String associatedSourceVolume = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.LOCAL_REPLICA_SOURCE_VOLUME.toString(),
                unManagedVolume.getVolumeInformation());
        // Skip autotierpolicy validation for clones as we use same orchestration for both volume & clone.
        if (null != associatedSourceVolume) {
            return;
        } else {
            super.validateAutoTierPolicy(autoTierPolicyId, unManagedVolume, vPool);
        }
    }
}
