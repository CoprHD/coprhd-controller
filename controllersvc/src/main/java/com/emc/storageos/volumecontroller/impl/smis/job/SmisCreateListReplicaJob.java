/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;

import com.emc.storageos.db.client.model.SynchronizationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

public class SmisCreateListReplicaJob extends SmisReplicaCreationJobs {

    private static final Logger _log = LoggerFactory.getLogger(SmisCreateListReplicaJob.class);
    private Map<String, URI> _srcNativeIdToReplicaUriMap;
    private Map<String, String> _tgtToSrcMap;
    private int _syncType;
    private Boolean _isSyncActive;

    public SmisCreateListReplicaJob(CIMObjectPath job, URI storgeSystemURI, Map<String, URI> srcNativeIdToReplicaUriMap,
            Map<String, String> tgtToSrcMap, int syncType, Boolean syncActive, TaskCompleter taskCompleter) {
        super(job, storgeSystemURI, taskCompleter, "CreateListReplica");
        this._srcNativeIdToReplicaUriMap = srcNativeIdToReplicaUriMap;
        this._tgtToSrcMap = tgtToSrcMap;
        this._syncType = syncType;
        this._isSyncActive = syncActive;
    }

    public void updateStatus(JobContext jobContext) throws Exception {
        CloseableIterator<CIMInstance> syncVolumeIter = null;
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        try {
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }

            List<? extends BlockObject> replicas = BlockObject.fetch(dbClient, getTaskCompleter().getIds());
            if (jobStatus == JobStatus.SUCCESS) {
                CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
                WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);

                if (_syncType == SmisConstants.MIRROR_VALUE || _syncType == SmisConstants.CLONE_VALUE) {
                    updatePools(client, dbClient, (List<? extends Volume>) replicas);
                }

                syncVolumeIter = client.associatorInstances(getCimJob(), null, SmisConstants.CIM_STORAGE_VOLUME, null, null, false, _volumeProps);
                StorageSystem storage = dbClient.queryObject(StorageSystem.class, getStorageSystemURI());
                processListReplica(syncVolumeIter, client, dbClient, jobContext.getSmisCommandHelper(), storage, replicas, _syncType, _isSyncActive);
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                _log.info("Failed to create list relica");
                for (BlockObject replica : replicas) {
                    replica.setInactive(true);
                }
                dbClient.persistObject(replicas);
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Encountered an internal error during create list replica job status processing: " + e.getMessage());
            _log.error("Caught an exception while trying to updateStatus for SmisCreateListReplicaJob", e);
        } finally {
            if (syncVolumeIter != null) {
                syncVolumeIter.close();
            }
            super.updateStatus(jobContext);
        }
    }

    protected void processListReplica(CloseableIterator<CIMInstance> syncVolumeIter, WBEMClient client,
            DbClient dbClient, SmisCommandHelper helper, StorageSystem storage, List<? extends BlockObject> replicas, int syncType, boolean isSyncActive)
                    throws Exception {
        // Get mapping of target Id to source Id
        Map<String, String> tgtIdToSrcIdMap = !_tgtToSrcMap.isEmpty() ? _tgtToSrcMap : getConsistencyGroupSyncPairs(dbClient, helper, storage, _srcNativeIdToReplicaUriMap.keySet(),
                syncType);

        Calendar now = Calendar.getInstance();
        while (syncVolumeIter.hasNext()) {
            // Get the sync volume native device id
            CIMInstance syncVolume = syncVolumeIter.next();
            CIMObjectPath syncVolumePath = syncVolume.getObjectPath();
            String syncDeviceID = syncVolumePath.getKey(SmisConstants.CP_DEVICE_ID).getValue().toString();
            String elementName = CIMPropertyFactory.getPropertyValue(syncVolume, SmisConstants.CP_ELEMENT_NAME);
            String wwn = CIMPropertyFactory.getPropertyValue(syncVolume, SmisConstants.CP_WWN_NAME);
            String alternateName = CIMPropertyFactory.getPropertyValue(syncVolume, SmisConstants.CP_NAME);

            // Get the associated volume for this sync volume
            String volumeDeviceID = tgtIdToSrcIdMap.get(syncDeviceID);
            // Lookup the replica ID based on the source volume native device ID
            URI replicaURI = _srcNativeIdToReplicaUriMap.get(volumeDeviceID);
            BlockObject replica = BlockObject.fetch(dbClient, replicaURI);

            // In the case snapping an RP+VPlex target volume, we will only be creating a single
            // BlockSnapshot corresponding to the requested target. The corresponding backing
            // array consistency group will still be snapped so if we have multiple target volumes,
            // we need to perform this null check to avoid a NPE.
            if (!URIUtil.isType(replicaURI, BlockSnapshot.class) || replica != null) {
                replica.setNativeId(syncDeviceID);
                replica.setDeviceLabel(elementName);
                replica.setCreationTime(now);
                replica.setWWN(wwn.toUpperCase());
                replica.setAlternateName(alternateName);

                if (replica instanceof BlockSnapshot) {
                    BlockSnapshot snapshot = (BlockSnapshot) replica;
                    snapshot.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(storage, snapshot));
                    snapshot.setIsSyncActive(isSyncActive);
                    snapshot.setProvisionedCapacity(getProvisionedCapacityInformation(client, syncVolume));
                    snapshot.setAllocatedCapacity(getAllocatedCapacityInformation(client, syncVolume));
                } else if (replica instanceof BlockMirror) {
                    BlockMirror mirror = (BlockMirror) replica;
                    mirror.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(storage, mirror));
                    mirror.setSyncType(Integer.toString(syncType));
                    mirror.setSyncState(SynchronizationState.SYNCHRONIZED.name());
                    mirror.setProvisionedCapacity(getProvisionedCapacityInformation(client, syncVolume));
                    mirror.setAllocatedCapacity(getAllocatedCapacityInformation(client, syncVolume));
                } else if (replica instanceof Volume) {
                    Volume clone = (Volume) replica;
                    clone.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, clone));
                    clone.setSyncActive(isSyncActive);
                    if (isSyncActive) {
                        clone.setReplicaState(ReplicationState.SYNCHRONIZED.name());
                    } else {
                        clone.setReplicaState(ReplicationState.INACTIVE.name());
                    }

                    clone.setProvisionedCapacity(getProvisionedCapacityInformation(client, syncVolume));
                    clone.setAllocatedCapacity(getAllocatedCapacityInformation(client, syncVolume));
                }

                dbClient.persistObject(replica);
            }
        }
    }
}
