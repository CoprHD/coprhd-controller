/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import models.SupportedResourceTypes;

import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.pools.StoragePoolUpdate;
import com.emc.vipr.client.core.filters.IdFilter;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;

public class StoragePoolUtils {
    public static StoragePoolRestRep getStoragePool(String id) {
        return getStoragePool(uri(id));
    }

    public static StoragePoolRestRep getStoragePool(URI id) {
        try {
            return getViprClient().storagePools().get(id);
        }
        catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static List<StoragePoolRestRep> getStoragePools(String storageSystemId) {
        return getViprClient().storagePools().getByStorageSystem(uri(storageSystemId));
    }

    public static List<StoragePoolRestRep> getStoragePools(Collection<URI> ids) {
        return getViprClient().storagePools().getByIds(ids);
    }

    public static StoragePoolRestRep update(String id, StoragePoolUpdate param) {
        return update(uri(id), param);
    }

    public static StoragePoolRestRep update(URI id, StoragePoolUpdate param) {
        return getViprClient().storagePools().update(id, param);
    }

    public static void register(URI poolId, URI arrayId) {
        getViprClient().storagePools().register(poolId, arrayId);
    }

    public static void deregister(URI poolId) {
        getViprClient().storagePools().deregister(poolId);
    }

    public static List<StoragePoolRestRep> getStoragePoolsAssignedToVirtualArray(String virtualArrayId) {
        return getViprClient().storagePools().getByVirtualArray(uri(virtualArrayId));
    }

    public static List<StoragePoolRestRep> getStoragePoolsAssignableToVirtualArray(String virtualArrayId) {
        List<StoragePoolRestRep> assignedPools = getStoragePoolsAssignedToVirtualArray(virtualArrayId);
        List<URI> ids = Lists.newArrayList();
        for (StoragePoolRestRep pool : assignedPools) {
            if ((pool.getAssignedVirtualArrays() != null) && pool.getAssignedVirtualArrays().contains(virtualArrayId)) {
                ids.add(id(pool));
            }
        }
        return getViprClient().storagePools().getAll(new IdFilter<StoragePoolRestRep>(ids).notId());
    }

    public static boolean supportsThinProvisioning(StoragePoolRestRep pool) {
        return SupportedResourceTypes.supportsThin(pool.getSupportedResourceTypes());
    }
}
