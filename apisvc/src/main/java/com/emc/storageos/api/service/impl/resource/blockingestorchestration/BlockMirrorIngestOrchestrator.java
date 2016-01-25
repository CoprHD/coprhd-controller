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

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedConsistencyGroup;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;

public class BlockMirrorIngestOrchestrator extends BlockIngestOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(BlockMirrorIngestOrchestrator.class);

    @Override
    public <T extends BlockObject> T ingestBlockObjects(IngestionRequestContext requestContext, Class<T> clazz)
            throws IngestionException {

        UnManagedVolume unManagedVolume = requestContext.getCurrentUnmanagedVolume();
        boolean unManagedVolumeExported = requestContext.getVolumeContext().isVolumeExported();

        // Validate the unManagedVolume properties
        validateUnManagedVolume(unManagedVolume, requestContext.getVpool());
        validateParentNotRpProtected(unManagedVolume);

        // Check whether mirror already ingested or not.
        String mirrorNativeGuid = unManagedVolume.getNativeGuid().replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                VolumeIngestionUtil.VOLUME);
        BlockMirror mirrorObj = VolumeIngestionUtil.checkIfBlockMirrorExistsInDB(mirrorNativeGuid, _dbClient);
        // Check if ingested volume has exportmasks pending for ingestion.
        if (isExportIngestionPending(mirrorObj, unManagedVolume.getId(), unManagedVolumeExported)) {
            return clazz.cast(mirrorObj);
        }
        if (null == mirrorObj) {
            mirrorObj = createBlockMirror(mirrorNativeGuid, requestContext.getStorageSystem(), unManagedVolume,
                    requestContext.getVpool(), requestContext.getVarray(), requestContext.getProject(), requestContext.getTenant().getId(),
                    requestContext.getObjectsToBeCreatedMap(), requestContext.getUnManagedCGsToUpdate());
        }
        // Run this always when the volume is NO_PUBLIC_ACCESS
        if (markUnManagedVolumeInactive(requestContext, mirrorObj)) {
            logger.info("Marking UnManaged Volume {} as inactive, all the related replicas and parent has been ingested",
                    unManagedVolume.getNativeGuid());
            // mark inactive if this is not to be exported. Else, mark as inactive after successful export
            if (!unManagedVolumeExported) {
                unManagedVolume.setInactive(true);
                requestContext.getUnManagedVolumesToBeDeleted().add(unManagedVolume);
            }
        } else {
            logger.info("Not all the parent/replicas of unManagedVolume {} have been ingested , hence marking as internal",
                    unManagedVolume.getNativeGuid());
            mirrorObj.addInternalFlags(INTERNAL_VOLUME_FLAGS);
        }
        // update UnManaged CG to update ingested volumes
        updateUnManagedCGWithVolumesIngested(unManagedVolume, _dbClient);
        return clazz.cast(mirrorObj);
    }

    /**
     * ViPR doesn't support creating mirrors off RP protected volumes. So check if the mirror to be ingested has RP
     * protected parent. If yes, throw an ingestion exception
     * 
     * @param unManagedVolume
     */
    private void validateParentNotRpProtected(UnManagedVolume unManagedVolume) {
        String parentNativeGUID = null;
        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        if (unManagedVolumeInformation.containsKey(SupportedVolumeInformation.LOCAL_REPLICA_SOURCE_VOLUME.toString())) {
            parentNativeGUID = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.LOCAL_REPLICA_SOURCE_VOLUME.toString(),
                    unManagedVolumeInformation);
        } else if (unManagedVolumeInformation.containsKey(SupportedVolumeInformation.VPLEX_PARENT_VOLUME.toString())) {
            parentNativeGUID = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.VPLEX_PARENT_VOLUME.toString(),
                    unManagedVolumeInformation);
        }
        if (parentNativeGUID != null) {
            logger.info("Finding unmanagedvolume {} in vipr db", parentNativeGUID);
            UnManagedVolume parentUnManagedVolume = null;
            URIQueryResultList umvUriList = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getVolumeInfoNativeIdConstraint(parentNativeGUID), umvUriList);
            if (umvUriList.iterator().hasNext()) {
                logger.info("Found unmanagedvolume {} in vipr db", parentNativeGUID);
                URI umvUri = umvUriList.iterator().next();
                parentUnManagedVolume = _dbClient.queryObject(UnManagedVolume.class, umvUri);
                if (parentUnManagedVolume != null && !parentUnManagedVolume.getInactive()
                        && VolumeIngestionUtil.checkUnManagedResourceIsRecoverPointEnabled(parentUnManagedVolume)) {
                    logger.info("Unmanaged mirror {} has RP protected parent", unManagedVolume.getLabel());
                    throw IngestionException.exceptions.cannotIngestMirrorsOfRPVolumes(unManagedVolume.getLabel(),
                            parentUnManagedVolume.getLabel());
                }
            }
        }
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
            VirtualPool vPool, VirtualArray vArray, Project project, URI tenantURI, Map<String, BlockObject> objectsToBeCreatedMap,
            List<UnManagedConsistencyGroup> umcgsToUpdate) {
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
        URI cgUri = getConsistencyGroupUri(unManagedVolume, vPool, project.getId(), tenantURI, vArray.getId(), _dbClient);
        if (null != cgUri) {
            updateCGPropertiesInVolume(cgUri, mirror, system, unManagedVolume);
            updateConsistencyGroupForRemainingVolumes(cgUri, unManagedVolume, objectsToBeCreatedMap, umcgsToUpdate, _dbClient);
        }
        String autoTierPolicyId = getAutoTierPolicy(unManagedVolume, system, vPool);
        validateAutoTierPolicy(autoTierPolicyId, unManagedVolume, vPool);
        if (null != autoTierPolicyId) {
            updateTierPolicyProperties(autoTierPolicyId, mirror);
        }
        return mirror;
    }
}
