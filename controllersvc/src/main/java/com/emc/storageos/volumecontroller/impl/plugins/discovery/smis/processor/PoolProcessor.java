/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

import javax.cim.CIMObjectPath;

import java.io.IOException;
import java.net.URI;
import java.util.List;

public abstract class PoolProcessor extends Processor {

    /**
     * return a StorageSystem object for a given systemId.
     * 
     * @param dbClient
     * @param systemId
     * @return
     * @throws IOException
     */
    protected StorageSystem getStorageSystem(DbClient dbClient, URI systemId)
            throws IOException {
        return dbClient.queryObject(StorageSystem.class, systemId);
    }

    /**
     * return the NativeID from the InstanceId.
     * 
     * @param instanceID
     * @return
     */
    protected String getNativeIDFromInstance(String instanceID) {
        // Get the poolId by splitting the instanceID.
        Iterable<String> poolIDItr = Splitter.on(Constants.PATH_DELIMITER_PATTERN).limit(3)
                .split(instanceID);
        return Iterables.getLast(poolIDItr);
    }

    protected String getPoolIdFromCapabilities(CIMObjectPath poolCapabilitiesPath) {
        String instanceID = poolCapabilitiesPath.getKey(Constants.INSTANCEID).getValue()
                .toString();
        if (null == instanceID) {
            return null;
        }
        return getNativeIDFromInstance(instanceID);
    }

    /**
     * Check if Pool exists in DB.
     * 
     * @param poolInstance
     * @param _dbClient
     * @param profile
     * @return
     * @throws IOException
     */
    protected StoragePool checkStoragePoolExistsInDB(
            String poolID, DbClient _dbClient, StorageSystem device) throws IOException {
        String nativeGuid = NativeGUIDGenerator
                .generateNativeGuid(device, poolID, NativeGUIDGenerator.POOL);
        return checkStoragePoolExistsInDB(nativeGuid, _dbClient);
    }

    /**
     * Check if Pool exists in DB.
     * 
     * @param poolInstance
     * @param _dbClient
     * @param profile
     * @return
     * @throws IOException
     */
    protected StoragePool checkStoragePoolExistsInDB(
            String nativeGuid, DbClient _dbClient) throws IOException {
        StoragePool pool = null;
        // use NativeGuid to lookup Pools in DB
        List<StoragePool> poolInDB = CustomQueryUtility.getActiveStoragePoolByNativeGuid(_dbClient, nativeGuid);
        if (poolInDB != null && !poolInDB.isEmpty()) {
            pool = poolInDB.get(0);
        }
        return pool;
    }

    /**
     * Returns a string that describes, succinctly, the SLO policy
     * 
     * @param slo [in] SLO name (EMCSLO)
     * @param workload [in] Workload name (EMCWorkload)
     * @param avgResponseTime [in] Average Expected Response time for the SLO + Workload (EMCApproxAverageResponseTime)
     * @return A String that represents a combination of the attributes
     */
    protected String generateSLOPolicyName(String slo, String workload, String avgResponseTime) {
        String result;
        if (Constants.NOT_AVAILABLE.equals(avgResponseTime)) {
            if (workload.equalsIgnoreCase(Constants.NONE)) {
                result = String.format("%s SLO", slo);
            } else {
                result = String.format("%s SLO %s Workload",
                        slo, workload);
            }
        } else {
            if (workload.equalsIgnoreCase(Constants.NONE)) {
                result = String.format("%s SLO (%sms)", slo, avgResponseTime);
            } else {
                result = String.format("%s SLO %s Workload (%sms)",
                        slo, workload, avgResponseTime);
            }
        }

        return result;
    }

}
