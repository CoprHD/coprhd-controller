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

import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;

public class BlockMirrorIngestOrchestrator extends BlockIngestOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(BlockMirrorIngestOrchestrator.class);

    @Override
    public <T extends BlockObject> T ingestBlockObjects(List<URI> systemCache, List<URI> poolCache, StorageSystem system,
            UnManagedVolume unManagedVolume,
            VirtualPool vPool, VirtualArray virtualArray, Project project, TenantOrg tenant,
            List<UnManagedVolume> unManagedVolumesIngestedSuccessfully,
            Map<String, BlockObject> createdObjectMap, Map<String, List<DataObject>> updatedObjectMap, boolean unManagedVolumeExported,
            Class<T> clazz,
            Map<String, StringBuffer> taskStatusMap, String vplexIngestionMethod) throws IngestionException {

        // Validate the unManagedVolume properties
        validateUnManagedVolume(unManagedVolume, vPool);

        // Check whether mirror already ingested or not.
        String mirrorNativeGuid = unManagedVolume.getNativeGuid().replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                VolumeIngestionUtil.VOLUME);
        BlockMirror mirrorObj = VolumeIngestionUtil.checkIfBlockMirrorExistsInDB(mirrorNativeGuid, _dbClient);
        // Check if ingested volume has exportmasks pending for ingestion.
        if (isExportIngestionPending(mirrorObj, unManagedVolume.getId(), unManagedVolumeExported)) {
            return clazz.cast(mirrorObj);
        }
        if (null == mirrorObj) {
            mirrorObj = createBlockMirror(mirrorNativeGuid, system, unManagedVolume, vPool, virtualArray, project);
        }
        // Run this always when the volume is NO_PUBLIC_ACCESS
        if (markUnManagedVolumeInactive(unManagedVolume, mirrorObj, unManagedVolumesIngestedSuccessfully, createdObjectMap,
                updatedObjectMap, taskStatusMap, vplexIngestionMethod)) {
            logger.info("Marking UnManaged Volume {} as inactive, all the related replicas and parent has been ingested",
                    unManagedVolume.getNativeGuid());
            // mark inactive if this is not to be exported. Else, mark as inactive after successful export
            if (!unManagedVolumeExported) {
                unManagedVolume.setInactive(true);
                unManagedVolumesIngestedSuccessfully.add(unManagedVolume);
            }
        } else {
            logger.info("Not all the parent/replicas of unManagedVolume {} have been ingested , hence marking as internal",
                    unManagedVolume.getNativeGuid());
            mirrorObj.addInternalFlags(INTERNAL_VOLUME_FLAGS);
        }

        return clazz.cast(mirrorObj);
    }

    /**
     * Create block Mirror object from the UnManagedVolume object.
     * 
     * @param unManagedVolume
     * @param system
     * @param volume
     * @return
     */
    private BlockMirror createBlockMirror(String nativeGuid, StorageSystem system, UnManagedVolume unManagedVolume,
            VirtualPool vPool, VirtualArray vArray, Project project) {
        BlockMirror mirror = new BlockMirror();
        mirror.setId(URIUtil.createId(BlockMirror.class));
        mirror.setInactive(false);

        StoragePool pool = _dbClient.queryObject(StoragePool.class, unManagedVolume.getStoragePoolUri());
        updateVolume(mirror, system, nativeGuid, pool, vArray, vPool, unManagedVolume, project);
        String syncInstance = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.SYNCHRONIZED_INSTANCE.toString(),
                unManagedVolume.getVolumeInformation());
        mirror.setSynchronizedInstance(syncInstance);
        String syncState = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.SYNC_STATE.toString(), unManagedVolume.getVolumeInformation());
        mirror.setSyncState(syncState);
        String syncType = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.SYNC_TYPE.toString(), unManagedVolume.getVolumeInformation());
        mirror.setSyncType(syncType);
        String autoTierPolicyId = getAutoTierPolicy(unManagedVolume, system, vPool);
        validateAutoTierPolicy(autoTierPolicyId, unManagedVolume, vPool);
        if (null != autoTierPolicyId) {
            updateTierPolicyProperties(autoTierPolicyId, mirror);
        }
        return mirror;
    }
}
