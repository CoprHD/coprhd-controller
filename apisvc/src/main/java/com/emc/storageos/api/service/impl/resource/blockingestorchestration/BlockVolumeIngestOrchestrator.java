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

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestStrategyFactory.IngestStrategyEnum;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestStrategyFactory.ReplicationStrategy;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestStrategyFactory.VolumeType;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedConsistencyGroup;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;

/**
 * Responsible for ingesting block local volumes.
 */
public class BlockVolumeIngestOrchestrator extends BlockIngestOrchestrator {

    private static final Logger _logger = LoggerFactory.getLogger(BlockVolumeIngestOrchestrator.class);

    // A reference to the ingest strategy factory.
    protected IngestStrategyFactory ingestStrategyFactory;

    /**
     * Setter for the ingest strategy factory.
     * 
     * @param ingestStrategyFactory A reference to the ingest strategy factory.
     */
    public void setIngestStrategyFactory(IngestStrategyFactory ingestStrategyFactory) {
        this.ingestStrategyFactory = ingestStrategyFactory;
    }

    @Override
    protected <T extends BlockObject> T ingestBlockObjects(IngestionRequestContext requestContext, Class<T> clazz)
            throws IngestionException {

        UnManagedVolume unManagedVolume = requestContext.getCurrentUnmanagedVolume();
        boolean unManagedVolumeExported = requestContext.getVolumeContext().isVolumeExported();
        Volume volume = null;
        List<BlockSnapshotSession> snapSessions = new ArrayList<BlockSnapshotSession>();

        URI unManagedVolumeUri = unManagedVolume.getId();
        String volumeNativeGuid = unManagedVolume.getNativeGuid().replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                VolumeIngestionUtil.VOLUME);

        volume = VolumeIngestionUtil.checkIfVolumeExistsInDB(volumeNativeGuid, _dbClient);
        // Check if ingested volume has export masks pending for ingestion.
        if (isExportIngestionPending(volume, unManagedVolumeUri, unManagedVolumeExported)) {
            return clazz.cast(volume);
        }

        if (null == volume) {
            validateUnManagedVolume(unManagedVolume, requestContext.getVpool());
            // @TODO Need to revisit this. In 8.x Provider, ReplicationGroup is automatically created when a volume is associated to a
            // StorageGroup.
            // checkUnManagedVolumeAddedToCG(unManagedVolume, virtualArray, tenant, project, vPool);
            checkVolumeExportState(unManagedVolume, unManagedVolumeExported);
            checkVPoolValidForExportInitiatorProtocols(requestContext.getVpool(), unManagedVolume);
            checkHostIOLimits(requestContext.getVpool(), unManagedVolume, unManagedVolumeExported);

            StoragePool pool = validateAndReturnStoragePoolInVAarray(unManagedVolume, requestContext.getVarray());

            // validate quota is exceeded for storage systems and pools
            checkSystemResourceLimitsExceeded(requestContext.getStorageSystem(), unManagedVolume,
                    requestContext.getExhaustedStorageSystems());
            checkPoolResourceLimitsExceeded(requestContext.getStorageSystem(), pool, unManagedVolume, requestContext.getExhaustedPools());
            String autoTierPolicyId = getAutoTierPolicy(unManagedVolume, requestContext.getStorageSystem(), requestContext.getVpool());
            validateAutoTierPolicy(autoTierPolicyId, unManagedVolume, requestContext.getVpool());

            volume = createVolume(requestContext.getStorageSystem(), volumeNativeGuid, pool,
                    requestContext.getVarray(), requestContext.getVpool(), unManagedVolume,
                    requestContext.getProject(), requestContext.getTenant(), autoTierPolicyId, requestContext.getObjectsToBeCreatedMap(),
                    requestContext.getUnManagedCGsToUpdate(), requestContext.getObjectsToBeUpdatedMap());
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
            StringSet syncAspectInfoForVolume = PropertySetterUtil.extractValuesFromStringSet(
                    SupportedVolumeInformation.SNAPSHOT_SESSIONS.toString(), unManagedVolume.getVolumeInformation());
            if ((syncAspectInfoForVolume != null) && (!syncAspectInfoForVolume.isEmpty())) {
                for (String syncAspectInfo : syncAspectInfoForVolume) {
                    String[] syncAspectInfoComponents = syncAspectInfo.split(":");
                    String syncAspectName = syncAspectInfoComponents[0];
                    String syncAspectObjPath = syncAspectInfoComponents[1];

                    // Make sure it is not already created.
                    URIQueryResultList queryResults = new URIQueryResultList();
                    _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getBlockSnapshotSessionBySessionInstance(syncAspectObjPath),
                            queryResults);
                    Iterator<URI> queryResultsIter = queryResults.iterator();
                    if (!queryResultsIter.hasNext()) {
                        BlockSnapshotSession session = new BlockSnapshotSession();
                        session.setId(URIUtil.createId(BlockSnapshotSession.class));
                        session.setLabel(syncAspectName);
                        session.setSessionLabel(syncAspectName);
                        session.setParent(new NamedURI(volume.getId(), volume.getLabel()));
                        session.setProject(new NamedURI(requestContext.getProject().getId(), volume.getLabel()));
                        session.setSessionInstance(syncAspectObjPath);
                        StringSet linkedTargetURIs = new StringSet();
                        URIQueryResultList snapshotQueryResults = new URIQueryResultList();
                        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getBlockSnapshotBySettingsInstance(syncAspectObjPath),
                                snapshotQueryResults);
                        Iterator<URI> snapshotQueryResultsIter = snapshotQueryResults.iterator();
                        while (snapshotQueryResultsIter.hasNext()) {
                            linkedTargetURIs.add(snapshotQueryResultsIter.next().toString());
                        }
                        session.setLinkedTargets(linkedTargetURIs);
                        session.setOpStatus(new OpStatusMap());
                        snapSessions.add(session);
                    }
                }
                if (!snapSessions.isEmpty()) {
                    _dbClient.createObject(snapSessions);
                }
            }
        }

        // Note that a VPLEX backend volume can also be a snapshot target volume.
        // When the VPLEX ingest orchestrator is executed, it gets the ingestion
        // strategy for the backend volume and executes it. If the backend volume
        // is both a snapshot and a VPLEX backend volume, this local volume ingest
        // strategy is invoked and a Volume instance will result. That is fine because
        // we need to represent that VPLEX backend volume. However, we also need a
        // BlockSnapshot instance to represent the snapshot target volume. Therefore,
        // if the unmanaged volume is also a snapshot target volume, we get and
        // execute the local snapshot ingest strategy to create this BlockSnapshot
        // instance and we add it to the created object list. Note that since the
        // BlockSnapshot is added to the created objects list and the Volume and
        // BlockSnapshot instance will have the same native GUID, we must be careful
        // about adding the Volume to the created object list in the VPLEX ingestion
        // strategy.
        BlockObject snapshot = null;
        if (VolumeIngestionUtil.isSnapshot(unManagedVolume)) {
            String strategyKey = ReplicationStrategy.LOCAL.name() + "_" + VolumeType.SNAPSHOT.name();
            IngestStrategy ingestStrategy = ingestStrategyFactory.getIngestStrategy(IngestStrategyEnum.getIngestStrategy(strategyKey));
            snapshot = ingestStrategy.ingestBlockObjects(requestContext, BlockSnapshot.class);
            requestContext.getObjectsToBeCreatedMap().put(snapshot.getNativeGuid(), snapshot);
        }

        // Run this always when volume NO_PUBLIC_ACCESS
        if (markUnManagedVolumeInactive(requestContext, volume)) {
            _logger.info("All the related replicas and parent has been ingested ",
                    unManagedVolume.getNativeGuid());
            // mark inactive if this is not to be exported. Else, mark as
            // inactive after successful export
            if (!unManagedVolumeExported) {
                unManagedVolume.setInactive(true);
                requestContext.getUnManagedVolumesToBeDeleted().add(unManagedVolume);
            }
        } else if (volume != null) {
            _logger.info(
                    "Not all the parent/replicas of unManagedVolume {} have been ingested , hence marking as internal",
                    unManagedVolume.getNativeGuid());
            volume.addInternalFlags(INTERNAL_VOLUME_FLAGS);
            for (BlockSnapshotSession snapSession : snapSessions) {
                snapSession.addInternalFlags(INTERNAL_VOLUME_FLAGS);
            }
            _dbClient.updateObject(snapSessions);
        }
        return clazz.cast(volume);
    }

    @Override
    protected BlockConsistencyGroup getConsistencyGroup(String volumeNativeGuid, UnManagedVolume unManagedVolume, VirtualPool vPool,
            URI project, URI tenant,
            URI virtualArray, List<UnManagedConsistencyGroup> umcgsToUpdate, DbClient dbClient) {
        // @TODO add check for RP as well. We should update backend volumes after ingesting vplex virtual volume.
        if (unManagedVolume.getVolumeCharacterstics().containsKey(SupportedVolumeCharacterstics.IS_VPLEX_BACKEND_VOLUME.toString())) {
            return null;
        }
        if (VolumeIngestionUtil.checkUnManagedResourceAddedToConsistencyGroup(unManagedVolume)) {
            return VolumeIngestionUtil.getBlockObjectConsistencyGroup(volumeNativeGuid, unManagedVolume, vPool, project, tenant,
                    virtualArray, umcgsToUpdate, dbClient);
        }
        return null;
    }

    @Override
    protected void updateCGPropertiesInVolume(BlockConsistencyGroup consistencyGroup, BlockObject blockObj,
            StorageSystem system, UnManagedVolume unManagedVolume, Map<String, BlockObject> objectsToBeCreatedMap,
            List<UnManagedConsistencyGroup> umcgsToUpdate, Map<String, List<DataObject>> objectsToUpdate) {
        List<DataObject> blockObjectsToUpdate = new ArrayList<DataObject>();
        UnManagedConsistencyGroup umcg = VolumeIngestionUtil.getUnManagedConsistencyGroup(unManagedVolume, _dbClient);
        if (null != consistencyGroup && null != umcg.getManagedVolumesMap() && !umcg.getManagedVolumesMap().isEmpty()) {
            for (String volumeNativeGuid : umcg.getManagedVolumesMap().keySet()) {
                BlockObject blockObject = objectsToBeCreatedMap.get(volumeNativeGuid);
                if (blockObject == null) {
                    // check if the volume has already been ingested
                    String ingestedVolumeURI = umcg.getManagedVolumesMap().get(volumeNativeGuid);
                    blockObject = BlockObject.fetch(_dbClient, URI.create(ingestedVolumeURI));
                    if (null == blockObject) {
                        _logger.warn("Unable to locate volume {} which is part of a consistency group ingestion operation",
                                volumeNativeGuid);
                        continue;
                    }
                    _logger.info("Volume {} was ingested as part of previous ingestion operation.", blockObject.getLabel());
                    blockObject.setConsistencyGroup(consistencyGroup.getId());
                } else {
                    _logger.info("Adding ingested volume {} to consistency group {}", blockObject.getLabel(), consistencyGroup.getLabel());
                    blockObject.setConsistencyGroup(consistencyGroup.getId());
                }
                blockObjectsToUpdate.add(blockObject);
            }
            if (blockObjectsToUpdate.size() == umcg.getManagedVolumesMap().keySet().size()) {
                umcg.setInactive(true);
                umcgsToUpdate.add(umcg);
            }
            objectsToUpdate.put(unManagedVolume.getNativeGuid(), blockObjectsToUpdate);
        }
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
