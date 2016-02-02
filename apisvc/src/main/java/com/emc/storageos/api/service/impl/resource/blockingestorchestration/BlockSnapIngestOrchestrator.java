/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.Iterator;
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
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

public class BlockSnapIngestOrchestrator extends BlockIngestOrchestrator {

    private static final Logger _logger = LoggerFactory.getLogger(BlockSnapIngestOrchestrator.class);

    @Override
    public <T extends BlockObject> T ingestBlockObjects(IngestionRequestContext requestContext, Class<T> clazz)
            throws IngestionException {

        UnManagedVolume unManagedVolume = requestContext.getCurrentUnmanagedVolume();
        boolean unManagedVolumeExported = requestContext.getVolumeContext().isVolumeExported();
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

            snapShot = createSnapshot(requestContext.getStorageSystem(), snapNativeGuid, 
                    requestContext.getVarray(unManagedVolume), requestContext.getVpool(unManagedVolume), 
                    unManagedVolume, requestContext.getProject());

            // See if this is a linked target for existing block snapshot sessions.
            if (!NullColumnValueGetter.isNullValue(snapShot.getSettingsInstance())) {
                URIQueryResultList queryResults = new URIQueryResultList();
                _dbClient.queryByConstraint(
                        AlternateIdConstraint.Factory.getBlockSnapshotSessionBySessionInstance(snapShot.getSettingsInstance()),
                        queryResults);
                Iterator<URI> queryResultsIter = queryResults.iterator();
                while (queryResultsIter.hasNext()) {
                    BlockSnapshotSession snapSession = _dbClient.queryObject(BlockSnapshotSession.class, queryResultsIter.next());
                    StringSet linkedTargets = snapSession.getLinkedTargets();
                    if ((linkedTargets != null)) {
                        linkedTargets.add(snapShot.getId().toString());
                    } else {
                        linkedTargets = new StringSet();
                        linkedTargets.add(snapShot.getId().toString());
                        snapSession.setLinkedTargets(linkedTargets);
        }
                    _dbClient.updateObject(snapSession);
                }
            }
        }

        // Run this logic always when Volume is NO_PUBLIC_ACCESS
        if (markUnManagedVolumeInactive(requestContext, snapShot)) {
            _logger.info("All the related replicas and parent of unManagedVolume {} has been ingested ", unManagedVolume.getNativeGuid());
            // mark inactive if this is not to be exported. Else, mark as inactive after successful export
            if (!unManagedVolumeExported) {
                unManagedVolume.setInactive(true);
                requestContext.getUnManagedVolumesToBeDeleted().add(unManagedVolume);
            }
        } else {
            _logger.info("Not all the parent/replicas of unManagedVolume {} have been ingested , hence marking as internal",
                    unManagedVolume.getNativeGuid());
            snapShot.addInternalFlags(INTERNAL_VOLUME_FLAGS);
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
