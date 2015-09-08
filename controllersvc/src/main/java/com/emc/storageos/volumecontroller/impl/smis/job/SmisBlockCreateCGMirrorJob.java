/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorCreateCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;
import com.google.common.base.Joiner;

public class SmisBlockCreateCGMirrorJob extends SmisBlockMirrorJob {
    private static final long serialVersionUID = 1L;
    private static final Logger _log = LoggerFactory.getLogger(SmisBlockCreateCGMirrorJob.class);
    private static final String RESERVED_CAPACITY_MAP = "reservedCapacityMap";

    public SmisBlockCreateCGMirrorJob(CIMObjectPath job, URI storgeSystemURI, TaskCompleter taskCompleter) {
        super(job, storgeSystemURI, taskCompleter, "CreateGroupMirrors");
    }

    public void updateStatus(JobContext jobContext) throws Exception {
        CloseableIterator<CIMInstance> syncVolumeIter = null;
        CloseableIterator<CIMObjectPath> repGroupPathIter = null;
        DbClient dbClient = jobContext.getDbClient();
        BlockMirrorCreateCompleter completer = (BlockMirrorCreateCompleter) getTaskCompleter();;
        JobStatus jobStatus = getJobStatus();
        try {
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }

            CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
            WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);
            List<BlockMirror> mirrors = dbClient.queryObject(BlockMirror.class, completer.getIds());

            if (jobStatus == JobStatus.SUCCESS || jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                updatePools(client, dbClient, mirrors);
            }

            if (jobStatus == JobStatus.SUCCESS) {
                _log.info("Group mirror creation success");
                repGroupPathIter = client.associatorNames(getCimJob(), null, SmisConstants.SE_REPLICATION_GROUP, null, null);
                CIMObjectPath repGroupPath = repGroupPathIter.next();
                StorageSystem storage = dbClient.queryObject(StorageSystem.class, getStorageSystemURI());
                String repGroupID = (String) repGroupPath.getKey(SmisConstants.CP_INSTANCE_ID).getValue();
                repGroupID = SmisUtils.getTargetGroupName(repGroupID, storage.getUsingSmis80());

                CIMInstance syncInst = getSynchronizedInstance(client, repGroupPath);
                String syncType = CIMPropertyFactory.getPropertyValue(syncInst, SmisConstants.CP_SYNC_TYPE);

                String[] props = { SmisConstants.CP_DEVICE_ID, SmisConstants.CP_ELEMENT_NAME, SmisConstants.CP_WWN_NAME, SmisConstants.CP_NAME, SmisConstants.CP_CONSUMABLE_BLOCKS, SmisConstants.CP_BLOCK_SIZE };
                syncVolumeIter = client.associatorInstances(repGroupPath, null, SmisConstants.CIM_STORAGE_VOLUME, null, null, false, props);
                processCGMirrors(syncVolumeIter, client, dbClient, jobContext.getSmisCommandHelper(), storage, mirrors, repGroupID, syncInst.getObjectPath().toString(), syncType);
            } else if (isJobInTerminalFailedState()) {
                _log.info("Failed to create group mirrors");
                completer.error(dbClient, DeviceControllerException.exceptions.attachVolumeMirrorFailed(getMessage()));
                for (BlockMirror mirror : mirrors) {
                    mirror.setInactive(true);
                }

                dbClient.persistObject(mirrors);
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Encountered an internal error during block create CG mirror job status processing: " +
                    e.getMessage());
            _log.error("Caught an exception while trying to updateStatus for SmisBlockCreateCGMirrorJob", e);
        } finally {
            if (syncVolumeIter != null) {
                syncVolumeIter.close();
            }

            if (repGroupPathIter != null) {
                repGroupPathIter.close();
            }

            super.updateStatus(jobContext);
        }
    }

    /**
     * Update storage pool capacity and remove reservation for mirror capacities from pool's reserved capacity map.
     *
     * @param client
     * @param dbClient
     * @param mirrors
     * @throws Exception
     */
    private void updatePools(WBEMClient client, DbClient dbClient, List<BlockMirror> mirrors) throws Exception {
        Set<URI> poolURIs = new HashSet<URI>();
        for (BlockMirror mirror : mirrors) {
            poolURIs.add(mirror.getPool());
        }

        List<StoragePool> pools = dbClient.queryObjectField(StoragePool.class, RESERVED_CAPACITY_MAP, poolURIs);
        for (StoragePool pool : pools) {
            SmisUtils.updateStoragePoolCapacity(dbClient, client, pool.getId());
            StringMap reservationMap = pool.getReservedCapacityMap();
            for (URI volumeId : getTaskCompleter().getIds()) {
                // remove from reservation map
                reservationMap.remove(volumeId.toString());
            }
        }

        dbClient.persistObject(pools);
    }

    /**
     * Iterate through all created sync volumes, match up with ViPR created mirrors, and update them in ViPR.
     *
     * @param syncVolumeIter
     * @param client
     * @param dbClient
     * @param helper
     * @param storage
     * @param mirrors
     * @param repGroupID
     * @param syncInst
     * @param syncType
     * @throws Exception
     */
    private void processCGMirrors(CloseableIterator<CIMInstance> syncVolumeIter, WBEMClient client, DbClient dbClient, SmisCommandHelper helper, StorageSystem storage,
                                 List<BlockMirror> mirrors, String repGroupID, String syncInst, String syncType) throws Exception {
        // Create mapping of volume.nativeDeviceId to BlockMirror object
        Map<String, BlockMirror> volIdToMirrorMap = new HashMap<String, BlockMirror>();
        for (BlockMirror mirror : mirrors) {
            Volume volume = dbClient.queryObject(Volume.class, mirror.getSource());
            volIdToMirrorMap.put(volume.getNativeId(), mirror);
        }

        // Get mapping of target Id to source Id
        Map<String, String> tgtToSrcMap = getConsistencyGroupSyncPairs(dbClient, helper, storage, volIdToMirrorMap.keySet());

        Calendar now = Calendar.getInstance();
        while (syncVolumeIter.hasNext()) {
            // Get the target mirror volume native device id
            CIMInstance syncVolume = syncVolumeIter.next();
            CIMObjectPath syncVolumePath = syncVolume.getObjectPath();
            String syncDeviceID = syncVolumePath.getKeyValue(SmisConstants.CP_DEVICE_ID).toString();
            String elementName = CIMPropertyFactory.getPropertyValue(syncVolume, SmisConstants.CP_ELEMENT_NAME);
            String wwn = CIMPropertyFactory.getPropertyValue(syncVolume, SmisConstants.CP_WWN_NAME);
            String alternateName = CIMPropertyFactory.getPropertyValue(syncVolume, SmisConstants.CP_NAME);

            // Get the associated volume for this sync volume
            String volumeDeviceID = tgtToSrcMap.get(syncDeviceID);
            // Lookup the associated source volume based on the volume native device id
            BlockMirror mirror = volIdToMirrorMap.get(volumeDeviceID);
            mirror.setReplicationGroupInstance(repGroupID);
            mirror.setProvisionedCapacity(getProvisionedCapacityInformation(client, syncVolume));
            mirror.setAllocatedCapacity(getAllocatedCapacityInformation(client, syncVolume));
            mirror.setWWN(wwn);
            mirror.setAlternateName(alternateName);
            mirror.setNativeId(syncDeviceID);
            mirror.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(storage, mirror));
            mirror.setDeviceLabel(elementName);
            mirror.setInactive(false);
            mirror.setCreationTime(now);
            mirror.setSynchronizedInstance(syncInst);
            mirror.setSyncType(syncType);
            dbClient.persistObject(mirror);
            _log.info(String.format("For target mirror volume %1$s, going to set BlockMirror %2$s nativeId to %3$s (%4$s). Associated volume is %5$s",
                    syncVolumePath.toString(), mirror.getId().toString(), syncDeviceID, elementName, volumeDeviceID));
        }
    }

    private Map<String, String> getConsistencyGroupSyncPairs(DbClient dbClient, SmisCommandHelper helper,
            StorageSystem storage, Collection<String> srcDevIds) throws WBEMException {
        Map<String, String> tgtToSrcMap = new HashMap<String, String>();
        List<CIMObjectPath> syncPairs = helper.getReplicationRelationships(
                storage, SmisConstants.LOCAL_LOCALITY_VALUE, SmisConstants.MIRROR_VALUE, SmisConstants.SYNCHRONOUS_MODE_VALUE,
                        SmisConstants.STORAGE_SYNCHRONIZED_VALUE);

        if (_log.isDebugEnabled()) {
            _log.debug("Found {} relationships", syncPairs.size());
            _log.debug("Looking for System elements on {} with IDs {}", storage.getNativeGuid(), Joiner.on(',').join(srcDevIds));
        }

        for (CIMObjectPath syncPair : syncPairs) {
            _log.info("Checking {}", syncPair);
            String srcProp = syncPair.getKeyValue(SmisConstants.CP_SYSTEM_ELEMENT).toString();
            CIMObjectPath srcPath = new CIMObjectPath(srcProp);
            String srcId = srcPath.getKeyValue(SmisConstants.CP_DEVICE_ID).toString();

            if (srcDevIds.contains(srcId)) {
                String tgtProp = syncPair.getKeyValue(SmisConstants.CP_SYNCED_ELEMENT).toString();
                CIMObjectPath tgtPath = new CIMObjectPath(tgtProp);
                String tgtId = tgtPath.getKeyValue(SmisConstants.CP_DEVICE_ID).toString();
                tgtToSrcMap.put(tgtId, srcId);
            }
        }

        return tgtToSrcMap;
    }
}
