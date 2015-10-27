/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
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

            // Create snapshot sessions for each synchronization aspect for the volume.
            // Not really sure where this goes?
            List<BlockSnapshotSession> snapSessions = new ArrayList<BlockSnapshotSession>();
            StringSet syncAspectInfoForVolume = PropertySetterUtil.extractValuesFromStringSet(
                    SupportedVolumeInformation.SNAPSHOT_SESSIONS.toString(), unManagedVolume.getVolumeInformation());
            if ((syncAspectInfoForVolume != null) && (!syncAspectInfoForVolume.isEmpty())) {
                for (String syncAspectInfo : syncAspectInfoForVolume) {
                    String[] syncAspectInfoComponents = syncAspectInfo.split(":");
                    String syncAspectName = syncAspectInfoComponents[0];
                    String syncAspectObjPath = syncAspectInfoComponents[1];
                    BlockSnapshotSession session = new BlockSnapshotSession();
                    session.setId(URIUtil.createId(BlockSnapshotSession.class));
                    session.setLabel(syncAspectName);
                    session.setSessionLabel(syncAspectName);
                    session.setParent(new NamedURI(volume.getId(), volume.getLabel()));
                    session.setProject(new NamedURI(project.getId(), volume.getLabel()));
                    session.setSessionInstance(syncAspectObjPath);
                    StringSet linkedTargetURIs = new StringSet();
                    URIQueryResultList queryResults = new URIQueryResultList();
                    _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getBlockSnapshotBySettingsInstance(syncAspectObjPath),
                            queryResults);
                    Iterator<URI> queryResultsIter = queryResults.iterator();
                    while (queryResultsIter.hasNext()) {
                        linkedTargetURIs.add(queryResultsIter.next().toString());
                    }
                    session.setLinkedTargets(linkedTargetURIs);
                    session.setOpStatus(new OpStatusMap());
                    snapSessions.add(session);
                }
                _dbClient.createObject(snapSessions);
            }
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
