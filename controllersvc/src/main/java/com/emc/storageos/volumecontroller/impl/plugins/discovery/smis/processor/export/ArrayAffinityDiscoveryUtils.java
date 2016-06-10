/*
 *  Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.google.common.collect.Collections2;

/*
 * Helper class to provide common functions for array affinity discovery
 */
public class ArrayAffinityDiscoveryUtils {
    private static final Logger _log = LoggerFactory.getLogger(ArrayAffinityDiscoveryUtils.class);
    public static final List<String> HOST_PROPERTIES = Arrays.asList("preferredPoolIds", "label");

    /**
     * update preferredPoolIds for a host
     *
     * @param Host host to be update
     * @param systemId ID of the system
     * @param dbClient DbClient
     * @param poolToTypeMap new preferred pools on the system
     * @return true if host's preferred pools have been changed
     */
    public static boolean updatePreferredPools(Host host, URI systemId, DbClient dbClient, Map<String, String> poolToTypeMap) {
        StringMap existingPreferredPools = host.getPreferredPoolIds();
        List<String> poolsToRemove = new ArrayList<String>();

        // find out pools on the system were preferred, but not anymore
        if (!existingPreferredPools.isEmpty()) {
            Collection<URI> poolURIs = Collections2.transform(existingPreferredPools.keySet(), CommonTransformerFunctions.FCTN_STRING_TO_URI);
            List<StoragePool> pools = dbClient.queryObject(StoragePool.class, poolURIs);
            for (StoragePool pool : pools) {
                if (systemId.equals(pool.getStorageDevice())) {
                    String poolIdStr = pool.getId().toString();
                    if (!poolToTypeMap.containsKey(poolIdStr)) {
                        poolsToRemove.add(poolIdStr);
                    }
                }
            }
        }

        boolean needUpdateHost = false;
        for (String pool : poolsToRemove) {
            existingPreferredPools.remove(pool);
            needUpdateHost = true;
        }

        if (!poolToTypeMap.isEmpty()) {
            existingPreferredPools.putAll(poolToTypeMap);
            needUpdateHost = true;
        }

        return needUpdateHost;
    }

    /*
     * Get storage pool URI if for the given volume
     *
     * @param client WBEMClient
     * @param volumePath CIMObjectPath
     * @return URI of storage pool
     */
    public static URI getStoragePool(CIMObjectPath volumePath, WBEMClient client, DbClient dbClient) {
        URI poolURI = null;
        CloseableIterator<CIMObjectPath> iterator = null;

        try {
            if (volumePath != null) {
                iterator = client.referenceNames(volumePath, SmisConstants.CIM_ALLOCATED_FROM_STORAGEPOOL, null);
                if (iterator.hasNext()) {
                    CIMObjectPath allocatedFromStoragePoolPath = iterator.next();
                    CIMObjectPath poolPath = (CIMObjectPath) allocatedFromStoragePoolPath.getKeyValue(SmisConstants.ANTECEDENT);
                    String nativeGuid = NativeGUIDGenerator.generateNativeGuidForPool(poolPath);
                    // use NativeGuid to lookup Pools in DB
                    List<StoragePool> pools = CustomQueryUtility.getActiveStoragePoolByNativeGuid(dbClient, nativeGuid);
                    if (pools != null && !pools.isEmpty()) {
                        poolURI = pools.get(0).getId();
                    }
                }
            }
        } catch (Exception e) {
            _log.error("Unable to get storage pool for volume {}", volumePath, e);
        } finally {
            if (iterator != null) {
                iterator.close();
            }
        }

        return poolURI;
    }

    /**
     * Add pool Id and export type to preferred pool map
     * If a host has at least one shared volume in given pool, the pool is shared (cluster type). If a host has only exclusive volumes in the pool,
     * the pool is not shared (Host type)
     *
     * @param preferredPools preferred pool to export type map
     * @param pool String of pool Id
     * @param type type of export
     */
    public static void addPoolToPreferredPoolMap(Map<String, String> preferredPoolToExportTypeMap, String pool, String type) {
        String oldType = preferredPoolToExportTypeMap.get(pool);
        if (oldType == null || (!oldType.equals(type) && type.equals(ExportGroupType.Cluster.name()))) {
            preferredPoolToExportTypeMap.put(pool, type);
        }
    }
}
