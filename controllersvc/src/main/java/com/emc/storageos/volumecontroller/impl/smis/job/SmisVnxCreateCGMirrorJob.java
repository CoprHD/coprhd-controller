/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorCreateCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;

public class SmisVnxCreateCGMirrorJob extends SmisBlockMirrorJob {
    private static final long serialVersionUID = 1L;
    private static final Logger _log = LoggerFactory.getLogger(SmisVnxCreateCGMirrorJob.class);
    private static final String RESERVED_CAPACITY_MAP = "reservedCapacityMap";
    private Map<String, String> tgtToSrcMap;

    public SmisVnxCreateCGMirrorJob(CIMObjectPath job, URI storgeSystemURI, Map<String, String> tgtToSrcMap, TaskCompleter taskCompleter) {
        super(job, storgeSystemURI, taskCompleter, "CreateGroupMirrors");
        this.tgtToSrcMap = tgtToSrcMap;
    }

    public void updateStatus(JobContext jobContext) throws Exception {
        CloseableIterator<CIMInstance> syncVolumeIter = null;
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        try {
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }
            BlockMirrorCreateCompleter completer = (BlockMirrorCreateCompleter) getTaskCompleter();
            List<BlockMirror> mirrors = dbClient.queryObject(BlockMirror.class, completer.getIds());

            CIMConnectionFactory cimConnectionFactory = jobContext.getCimConnectionFactory();
            WBEMClient client = getWBEMClient(dbClient, cimConnectionFactory);

            if (jobStatus == JobStatus.SUCCESS || jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                updatePools(client, dbClient, mirrors);
            }

            if (jobStatus == JobStatus.SUCCESS) {
                String[] props = { SmisConstants.CP_DEVICE_ID, SmisConstants.CP_ELEMENT_NAME, SmisConstants.CP_WWN_NAME, SmisConstants.CP_NAME, SmisConstants.CP_CONSUMABLE_BLOCKS, SmisConstants.CP_BLOCK_SIZE };
                syncVolumeIter = client.associatorInstances(getCimJob(), null, SmisConstants.CIM_STORAGE_VOLUME, null, null, false, props);
                StorageSystem storage = dbClient.queryObject(StorageSystem.class, getStorageSystemURI());
                processCGMirrors(syncVolumeIter, client, dbClient, storage, mirrors, UUID.randomUUID().toString());
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                _log.info("Failed to create group mirrors");
                for (BlockMirror mirror : mirrors) {
                    mirror.setInactive(true);
                }
                dbClient.persistObject(mirrors);
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Encountered an internal error during create CG mirror job status processing: " + e.getMessage());
            _log.error("Caught an exception while trying to updateStatus for SmisVnxCreateCGMirrorJob", e);
        } finally {
            if(syncVolumeIter != null) {
                syncVolumeIter.close();
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
     * @param storage
     * @param mirrors
     * @param repGroupID
     * @throws Exception
     */
    private void processCGMirrors(CloseableIterator<CIMInstance> syncVolumeIter, WBEMClient client, DbClient dbClient, StorageSystem storage,
                                 List<BlockMirror> mirrors, String repGroupID) throws Exception {
        // Create mapping of volume.nativeDeviceId to BlockMirror object
        Map<String, BlockMirror> volIdToMirrorMap = new HashMap<String, BlockMirror>();
        for (BlockMirror mirror : mirrors) {
            Volume volume = dbClient.queryObject(Volume.class, mirror.getSource());
            volIdToMirrorMap.put(volume.getNativeId(), mirror);
        }

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
            CIMInstance syncInstance = getStorageSyncInstanceFromVolume(client, syncVolumePath);
            mirror.setSynchronizedInstance(syncInstance.getObjectPath().toString());
            mirror.setSyncType(CIMPropertyFactory.getPropertyValue(syncInstance, SmisConstants.CP_SYNC_TYPE));

            dbClient.persistObject(mirror);
            _log.info(String.format("For target mirror volume %1$s, going to set BlockMirror %2$s nativeId to %3$s (%4$s). Associated volume is %5$s",
                    syncVolumePath.toString(), mirror.getId().toString(), syncDeviceID, elementName, volumeDeviceID));
        }
    }
}
