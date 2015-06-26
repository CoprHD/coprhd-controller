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

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;

public class SmisReplicaCreationJobs extends SmisJob {

    private static final Logger _log = LoggerFactory.getLogger(SmisReplicaCreationJobs.class);

    public SmisReplicaCreationJobs(CIMObjectPath cimJob, URI storageSystem,
            TaskCompleter taskCompleter, String jobName) {
        super(cimJob, storageSystem, taskCompleter, jobName);
        // TODO Auto-generated constructor stub
    }

    protected long getProvisionedCapacityInformation(WBEMClient client,
            CIMInstance syncVolumeInstance) {
        Long provisionedCapacity = 0L;
        try {
            if (syncVolumeInstance != null) {
                CIMProperty consumableBlocks = syncVolumeInstance
                        .getProperty(SmisConstants.CP_CONSUMABLE_BLOCKS);
                CIMProperty blockSize = syncVolumeInstance.getProperty(SmisConstants.CP_BLOCK_SIZE);
                // calculate provisionedCapacity = consumableBlocks * block size
                provisionedCapacity = Long.valueOf(consumableBlocks.getValue().toString())
                        * Long.valueOf(blockSize.getValue().toString());

            }

        } catch (Exception e) {
            _log.error("Updating ProvisionedCapacity failed for Volume {}",
                    syncVolumeInstance.getObjectPath(), e);
        }
        return provisionedCapacity;
    }

    protected long getAllocatedCapacityInformation(WBEMClient client, CIMInstance syncVolumeInstance) {
        Long allocatedCapacity = 0L;
        try {
            if (syncVolumeInstance != null) {
                CloseableIterator<CIMInstance> iterator = client.referenceInstances(
                        syncVolumeInstance.getObjectPath(),
                        SmisConstants.CIM_ALLOCATED_FROM_STORAGEPOOL, null, false,
                        SmisConstants.PS_SPACE_CONSUMED);
                if (iterator.hasNext()) {
                    CIMInstance allocatedFromStoragePoolPath = iterator.next();
                    CIMProperty spaceConsumed = allocatedFromStoragePoolPath
                            .getProperty(SmisConstants.CP_SPACE_CONSUMED);
                    allocatedCapacity = Long.valueOf(spaceConsumed.getValue().toString());

                }
            }
        } catch (Exception e) {
            _log.error("Updating Allocated Capacity failed for Volume {}",
                    syncVolumeInstance.getObjectPath(), e);
        }
        return allocatedCapacity;
    }
    
    /**
     * It will iterate through all created sync volumes, match up with ViPR created clones, and update them in ViPR.
     * @param syncVolumeIter
     * @param client
     * @param dbClient
     * @param clones
     * @param replicationGroupId
     * @throws Exception
     */
    protected void processCGClones(CloseableIterator<CIMObjectPath> syncVolumeIter, WBEMClient client, 
                                 DbClient dbClient, List<Volume> clones, String replicationGroupId, boolean isSyncActive) throws Exception {
        Map<String, URI> deviceIdToVolumeMap = new HashMap<String, URI>();
        Map<URI, Volume> sourceToCloneMap = new HashMap<URI, Volume>();
        Set<URI> pools = new HashSet<URI>();
        for ( Volume clone : clones) {
            Volume volume = dbClient.queryObject(Volume.class, clone.getAssociatedSourceVolume());
            deviceIdToVolumeMap.put(volume.getNativeId(), volume.getId());
            sourceToCloneMap.put(volume.getId(), clone);
            pools.add(clone.getPool());
        }
        
        for (URI pool : pools) {
            SmisUtils.updateStoragePoolCapacity(dbClient, client, pool);
            StoragePool thePool = dbClient.queryObject(StoragePool.class, pool);
            StringMap reservationMap = thePool.getReservedCapacityMap();
            for (URI volumeId : getTaskCompleter().getIds()) {
                // remove from reservation map
                reservationMap.remove(volumeId.toString());
            }
            dbClient.persistObject(thePool);
        }
        // Iterate through the clone elements that were created by the
        // Job and try to match them up with the appropriate ViPR clone
        
        Calendar now = Calendar.getInstance();
        while (syncVolumeIter.hasNext()) {
            // Get the sync volume native device id
            CIMObjectPath syncVolumePath = syncVolumeIter.next();
            CIMInstance syncVolume = client.getInstance(syncVolumePath, false, false, null);
            String syncDeviceID = syncVolumePath.getKey(SmisConstants.CP_DEVICE_ID).getValue().toString();
            String elementName = CIMPropertyFactory.getPropertyValue(syncVolume, SmisConstants.CP_ELEMENT_NAME);
            // Get the associated source volume for this sync volume
            CIMObjectPath volumePath = null;
            CloseableIterator<CIMObjectPath> volumeIter = client.associatorNames(syncVolumePath, null, SmisConstants.CIM_STORAGE_VOLUME, null, null);
            volumePath = volumeIter.next(); volumeIter.close();
            String volumeDeviceID = volumePath.getKey(SmisConstants.CP_DEVICE_ID).getValue().toString();
            String wwn = CIMPropertyFactory.getPropertyValue(syncVolume, SmisConstants.CP_WWN_NAME);
            String alternativeName =
                    CIMPropertyFactory.getPropertyValue(syncVolume,
                            SmisConstants.CP_NAME);
            // Lookup the associated clone based on the elemenat name, and set the associated volumes.
            URI sourceVolume = deviceIdToVolumeMap.get(volumeDeviceID);
            Volume theClone = sourceToCloneMap.get(sourceVolume);

            theClone.setNativeId(syncDeviceID);
            theClone.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, theClone));
            theClone.setReplicationGroupInstance(replicationGroupId);
            theClone.setDeviceLabel(elementName);
            theClone.setInactive(false);
            theClone.setSyncActive(isSyncActive);
            theClone.setCreationTime(now);
            theClone.setWWN(wwn.toUpperCase());
            theClone.setAlternateName(alternativeName);
            theClone.setProvisionedCapacity(getProvisionedCapacityInformation(client, syncVolume));
            theClone.setAllocatedCapacity(getAllocatedCapacityInformation(client, syncVolume));
            theClone.setInactive(false);
            if (isSyncActive) {
                theClone.setReplicaState(ReplicationState.CREATED.name());
            } else {
                theClone.setReplicaState(ReplicationState.INACTIVE.name());
            }
            dbClient.persistObject(theClone);
        }
    }

}
