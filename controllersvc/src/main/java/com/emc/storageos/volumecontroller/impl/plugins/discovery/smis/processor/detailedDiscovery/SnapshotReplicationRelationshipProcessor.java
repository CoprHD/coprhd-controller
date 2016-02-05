/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.cim.CIMArgument;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

/**
 * Processor to handle StorageSynchronized instances of single volume snapshots
 * and update corresponding BlockSnapshots in ViPR
 * 
 * Used in BLOCK_SNAPSHOTS discovery only
 */
public class SnapshotReplicationRelationshipProcessor extends StorageProcessor {
    private final static Logger _logger = LoggerFactory
            .getLogger(SnapshotReplicationRelationshipProcessor.class);
    private static final String COPY_STATE = "CopyState";
    private static final String EMC_RELATIONSHIP_NAME = "EMCRelationshipName";
    private static final String EMC_COPY_STATE_DESC = "EMCCopyStateDesc";
    private static final String INACTIVE = "INACTIVE";
    private static final String COPY_STATE_SYNCHRONIZED = "4";
    private static final String BLOCK_SNAPSHOT = "BlockSnapshot";

    private PartitionManager _partitionManager;
    private int _partitionSize;
    private DbClient _dbClient;
    private Map<String, Map<String, String>> _syncAspectMap;
    private List<BlockSnapshot> _updateSnapshotList;

    @SuppressWarnings("unchecked")
    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        _logger.debug("Calling SnapshotReplicationRelationshipProcessor");
        _dbClient = (DbClient) keyMap.get(Constants.dbClient);
        _syncAspectMap = (Map<String, Map<String, String>>) keyMap
                .get(Constants.SNAPSHOT_NAMES_SYNCHRONIZATION_ASPECT_MAP);
        _updateSnapshotList = new ArrayList<BlockSnapshot>();
        _partitionSize = getPartitionSize(keyMap);
        CIMInstance[] instances = (CIMInstance[]) getFromOutputArgs(
                (CIMArgument[]) resultObj, SmisConstants.SYNCHRONIZATIONS);
        if (instances == null) {
            _logger.info("No {} returned", SmisConstants.SYNCHRONIZATIONS);
            return;
        }

        _logger.info("Total StorageSynchronized instances {}", instances.length);
        processInstances(instances);

        if (!_updateSnapshotList.isEmpty()) {
            _partitionManager.updateInBatches(_updateSnapshotList,
                    _partitionSize, _dbClient, BLOCK_SNAPSHOT);
        }
    }

    private void processInstances(CIMInstance[] instances) {
        for (CIMInstance instance : instances) {
            try {
                CIMObjectPath targetPath = (CIMObjectPath) instance
                        .getPropertyValue(Constants._SyncedElement);
                String nativeGuid = getVolumeNativeGuid(targetPath);
                _logger.info("Processing snapshot {}", nativeGuid);

                BlockSnapshot snapshot = checkSnapShotExistsInDB(nativeGuid,
                        _dbClient);
                if (snapshot != null && !snapshot.hasConsistencyGroup()) {
                    if (updateSettingsInstance(instance, snapshot) ||
                            updateNeedsCopyToTarget(instance, snapshot) ||
                            updateIsSyncActive(instance, snapshot)) {
                        _logger.debug("Update Snapshot {}", snapshot.getLabel());
                        _updateSnapshotList.add(snapshot);
                        if (_updateSnapshotList.size() >= _partitionSize) {
                            _partitionManager.updateInBatches(_updateSnapshotList,
                                    _partitionSize, _dbClient,
                                    BLOCK_SNAPSHOT);
                            _updateSnapshotList.clear();
                        }
                    }
                }
            } catch (Exception e) {
                _logger.error("Exception on processing instances", e);
            }
        }
    }

    private boolean updateSettingsInstance(CIMInstance syncInstance,
            BlockSnapshot snapshot) {
        CIMObjectPath sourcePath = (CIMObjectPath) syncInstance
                .getPropertyValue(Constants._SystemElement);
        String srcNativeGuid = getUnManagedVolumeNativeGuidFromVolumePath(sourcePath);
        String relationshipName = getCIMPropertyValue(syncInstance,
                EMC_RELATIONSHIP_NAME);
        String syncAspect = null;
        Map<String, String> aspectsForSource = _syncAspectMap.get(srcNativeGuid);
        if (null != aspectsForSource) {
            syncAspect = aspectsForSource.get(getSyncAspectMapKey(srcNativeGuid, relationshipName));
        }
        String valueInDb = snapshot.getSettingsInstance();
        boolean isValueChanged = !(valueInDb == null ? syncAspect == null
                : valueInDb.equals(syncAspect));
        if (isValueChanged) {
            snapshot.setSettingsInstance(syncAspect);
        }

        return isValueChanged;
    }

    private boolean updateNeedsCopyToTarget(CIMInstance syncInstance,
            BlockSnapshot snapshot) {
        String emcCopyState = getCIMPropertyValue(syncInstance,
                EMC_COPY_STATE_DESC);
        // for an inactive snapshot, needsCopyToTarget has to be set
        boolean needsCopyToTarget = INACTIVE.equals(emcCopyState);
        Boolean valueInDb = snapshot.getNeedsCopyToTarget();
        boolean isValueChanged = valueInDb == null ? true
                : valueInDb != needsCopyToTarget;
        if (isValueChanged) {
            snapshot.setNeedsCopyToTarget(needsCopyToTarget);
        }

        return isValueChanged;
    }

    private boolean updateIsSyncActive(CIMInstance syncInstance,
            BlockSnapshot snapshot) {
        String copyState = getCIMPropertyValue(syncInstance, COPY_STATE);
        boolean inSync = COPY_STATE_SYNCHRONIZED.equals(copyState);
        Boolean valueInDb = snapshot.getIsSyncActive();
        boolean isValueChanged = valueInDb == null ? true : inSync != valueInDb
                .booleanValue();
        if (isValueChanged) {
            snapshot.setIsSyncActive(inSync);
        }

        return isValueChanged;
    }

    public void setPartitionManager(PartitionManager partitionManager) {
        _partitionManager = partitionManager;
    }
}
