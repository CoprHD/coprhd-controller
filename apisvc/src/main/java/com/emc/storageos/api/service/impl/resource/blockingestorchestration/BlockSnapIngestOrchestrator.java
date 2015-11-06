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
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

public class BlockSnapIngestOrchestrator extends BlockIngestOrchestrator {

    private static final Logger _logger = LoggerFactory.getLogger(BlockSnapIngestOrchestrator.class);

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
    public <T extends BlockObject> T ingestBlockObjects(List<URI> systemCache, List<URI> poolCache, StorageSystem system,
            UnManagedVolume unManagedVolume,
            VirtualPool vPool, VirtualArray virtualArray, Project project, TenantOrg tenant,
            List<UnManagedVolume> unManagedVolumesToBeDeleted,
            Map<String, BlockObject> createdObjectMap, Map<String, List<DataObject>> updatedObjectMap, boolean unManagedVolumeExported,
            Class<T> clazz,
            Map<String, StringBuffer> taskStatusMap, String vplexIngestionMethod) throws IngestionException {

        BlockSnapshot snapShot = null;

        String snapNativeGuid = unManagedVolume.getNativeGuid().replace(NativeGUIDGenerator.UN_MANAGED_VOLUME,
                NativeGUIDGenerator.VOLUME);

        snapShot = VolumeIngestionUtil.checkSnapShotExistsInDB(snapNativeGuid, _dbClient);

        // Check if ingested volume has exportmasks pending for ingestion.
        if (isExportIngestionPending(snapShot, unManagedVolume.getId(), unManagedVolumeExported)) {
            return clazz.cast(snapShot);
        }
        if (null == snapShot) {
            // @TODO Need to revisit this. In 8.x provider, Replication Group is
            // automatically created when a volume is associated to a
            // StorageGroup.
            // checkUnManagedVolumeAddedToCG(unManagedVolume, virtualArray,
            // tenant, project, vPool);
            checkVolumeExportState(unManagedVolume, unManagedVolumeExported);
            VolumeIngestionUtil.checkUnManagedResourceIngestable(unManagedVolume);
            VolumeIngestionUtil.checkUnManagedResourceIsRecoverPointEnabled(unManagedVolume);

            snapShot = createSnapshot(system, snapNativeGuid, virtualArray, vPool, unManagedVolume, project);
        }

        // Run this logic always when Volume is NO_PUBLIC_ACCESS
        if (markUnManagedVolumeInactive(unManagedVolume, snapShot, unManagedVolumesToBeDeleted, createdObjectMap, updatedObjectMap,
                taskStatusMap, vplexIngestionMethod)) {
            _logger.info("All the related replicas and parent of unManagedVolume {} has been ingested ", unManagedVolume.getNativeGuid());
            // mark inactive if this is not to be exported. Else, mark as inactive after successful export
            if (!unManagedVolumeExported) {
                unManagedVolume.setInactive(true);
                unManagedVolumesToBeDeleted.add(unManagedVolume);
            }
        } else {
            _logger.info("Not all the parent/replicas of unManagedVolume {} have been ingested , hence marking as internal",
                    unManagedVolume.getNativeGuid());
            snapShot.addInternalFlags(INTERNAL_VOLUME_FLAGS);
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
        if (VolumeIngestionUtil.isVplexBackendVolume(unManagedVolume)) {
            String strategyKey = ReplicationStrategy.LOCAL.name() + "_" + VolumeType.VOLUME.name();
            IngestStrategy ingestStrategy = ingestStrategyFactory.getIngestStrategy(IngestStrategyEnum.getIngestStrategy(strategyKey));
            BlockObject beVolumeObject = ingestStrategy.ingestBlockObjects(systemCache, poolCache,
                    system, unManagedVolume, vPool, virtualArray,
                    project, tenant, unManagedVolumesToBeDeleted, createdObjectMap,
                    updatedObjectMap, true, Volume.class, taskStatusMap, vplexIngestionMethod);
            createdObjectMap.put(beVolumeObject.getNativeGuid(), beVolumeObject);
        }

        return clazz.cast(snapShot);
    }

    /**
     * There is no validation required for snaps. Hence returning void argument.
     */
    @Override
    protected void validateAutoTierPolicy(String autoTierPolicyId, UnManagedVolume unManagedVolume, VirtualPool vPool) {
        return;
    }

    private BlockSnapshot createSnapshot(StorageSystem system, String nativeGuid, VirtualArray virtualArray,
            VirtualPool vPool, UnManagedVolume unManagedVolume, Project project) throws IngestionException {

        BlockSnapshot snapShot = new BlockSnapshot();
        snapShot.setId(URIUtil.createId(BlockSnapshot.class));
        snapShot.setNativeGuid(nativeGuid);
        updateBlockObjectNativeIds(snapShot, unManagedVolume);

        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        String deviceLabel = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.DEVICE_LABEL.toString(), unManagedVolumeInformation);
        if (null == deviceLabel || deviceLabel.trim().isEmpty()) {
            deviceLabel = nativeGuid;
        }
        snapShot.setSnapsetLabel(deviceLabel);// TODO: shld revisit this
        snapShot.setStorageController(system.getId());
        snapShot.setVirtualArray(virtualArray.getId());
        snapShot.setProject(new NamedURI(project.getId(), snapShot.getLabel()));
        snapShot.setWWN(unManagedVolume.getWwn());

        String allocatedCapacity = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.ALLOCATED_CAPACITY.toString(), unManagedVolume.getVolumeInformation());
        String provisionedCapacity = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.PROVISIONED_CAPACITY.toString(), unManagedVolume.getVolumeInformation());

        snapShot.setAllocatedCapacity(Long.parseLong(allocatedCapacity));
        snapShot.setProvisionedCapacity(Long.parseLong(provisionedCapacity));

        String syncActive = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.IS_SYNC_ACTIVE.toString(), unManagedVolume.getVolumeInformation());
        Boolean isSyncActive = (null != syncActive) ? Boolean.parseBoolean(syncActive) : false;
        snapShot.setIsSyncActive(isSyncActive);

        String readOnly = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.IS_READ_ONLY.toString(), unManagedVolume.getVolumeInformation());
        Boolean isReadOnly = (null != readOnly) ? Boolean.parseBoolean(readOnly) : false;
        snapShot.setIsReadOnly(isReadOnly);

        String settingsInstance = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.SETTINGS_INSTANCE.toString(), unManagedVolume.getVolumeInformation());
        snapShot.setSettingsInstance(settingsInstance);

        String needsCopyToTarget = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.NEEDS_COPY_TO_TARGET.toString(), unManagedVolumeInformation);
        Boolean isNeedsCopyToTarget = (null != needsCopyToTarget) ? Boolean.parseBoolean(needsCopyToTarget) : false;
        snapShot.setNeedsCopyToTarget(isNeedsCopyToTarget);

        String techType = PropertySetterUtil.extractValueFromStringSet(SupportedVolumeInformation.TECHNOLOGY_TYPE.toString(),
                unManagedVolumeInformation);
        snapShot.setTechnologyType(techType);

        return snapShot;

    }

}
