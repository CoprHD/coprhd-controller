/*
 *  Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringSet;
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

    /**
     * update preferredPoolIds for a host
     *
     * @param Host host to be update
     * @param systemId ID of the system
     * @param dbClient DbClient
     * @param preferredPools new preferred pools on the system
     * @return true if host's preferred pools have been changed
     */
    public static boolean updatePreferredPools(Host host, URI systemId, DbClient dbClient, Set<URI> preferredPools) {
        StringSet existingPreferredPools = host.getPreferredPoolIds();
        List<String> poolsToRemove = new ArrayList<String>();
        List<String> poolsToAdd = new ArrayList<String>();

        // find out pools on the system were preferred, but not anymore
        if (!existingPreferredPools.isEmpty()) {
            Collection<URI> poolURIs = Collections2.transform(existingPreferredPools, CommonTransformerFunctions.FCTN_STRING_TO_URI);
            List<StoragePool> pools = dbClient.queryObject(StoragePool.class, poolURIs);
            for (StoragePool pool : pools) {
                if (systemId.equals(pool.getStorageDevice())) {
                    if (!preferredPools.contains(pool.getId())) {
                        poolsToRemove.add(pool.getId().toString());
                    }
                }
            }
        }

        // find out new preferred pool
        if (!preferredPools.isEmpty()) {
            for (URI pool : preferredPools) {
                if (!existingPreferredPools.contains(pool.toString())) {
                    poolsToAdd.add(pool.toString());
                }
            }
        }

        boolean needUpdateHost = false;
        if (!poolsToRemove.isEmpty()) {
            existingPreferredPools.removeAll(poolsToRemove);
            needUpdateHost = true;
        }

        if (!poolsToAdd.isEmpty()) {
            existingPreferredPools.addAll(poolsToAdd);
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
}
