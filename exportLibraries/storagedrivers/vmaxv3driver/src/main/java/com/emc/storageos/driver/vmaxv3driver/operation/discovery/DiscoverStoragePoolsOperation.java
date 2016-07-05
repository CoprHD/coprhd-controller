/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.operation.discovery;

import com.emc.storageos.driver.vmaxv3driver.base.OperationImpl;
import com.emc.storageos.driver.vmaxv3driver.rest.SloprovisioningSymmetrixDirectorList;
import com.emc.storageos.driver.vmaxv3driver.rest.SloprovisioningSymmetrixSrpGet;
import com.emc.storageos.driver.vmaxv3driver.rest.SloprovisioningSymmetrixSrpList;
import com.emc.storageos.driver.vmaxv3driver.rest.bean.Srp;
import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of "DiscoverStoragePools" operation.
 *
 * Created by gang on 6/21/16.
 */
public class DiscoverStoragePoolsOperation extends OperationImpl {

    private static final Logger logger = LoggerFactory.getLogger(DiscoverStoragePoolsOperation.class);

    private StorageSystem storageSystem;
    private List<StoragePool> StoragePools;

    @Override
    public boolean isMatch(String name, Object... parameters) {
        if ("discoverStoragePools".equals(name)) {
            this.storageSystem = (StorageSystem) parameters[0];
            this.StoragePools = (List<StoragePool>) parameters[1];
            if (this.StoragePools == null) {
                this.StoragePools = new ArrayList<>();
            }
            this.setClient(this.storageSystem);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Perform the storage pool discovery operation. All the discovery information
     * will be set into the "StoragePools" instance.
     *
     * @return A map indicates if the operation succeeds or fails.
     */
    @Override
    public Map<String, Object> execute() {
        Map<String, Object> result = new HashMap<>();
        try {
            // Get storage pool list.
            String storageSystemId = this.storageSystem.getNativeId();
            List<String> srpIds= new SloprovisioningSymmetrixSrpList(storageSystemId).perform(this.getClient());
            for (String srpId : srpIds) {
                Srp item = new SloprovisioningSymmetrixSrpGet(storageSystemId, srpId).perform(this.getClient());
                StoragePool storagePool = new StoragePool();
                storagePool.setStorageSystemId(storageSystemId);
                storagePool.setNativeId(srpId);
                storagePool.setDeviceLabel(srpId);
                storagePool.setDisplayName(srpId);
                // Parse the needed attributes and set them into the returned bean.
                storagePool.setProtocols(this.getSupportedProtocols(storageSystemId));
                storagePool.setTotalCapacity(item.getTotal_usable_cap_gb().longValue());
                storagePool.setFreeCapacity(item.getTotal_usable_cap_gb().longValue() -
                    item.getTotal_allocated_cap_gb().longValue());
                storagePool.setSubscribedCapacity(item.getTotal_subscribed_cap_gb().longValue());
                storagePool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY);
                // Keep blank since no API to get according to Evgeny's reply.
                storagePool.setSupportedRaidLevels(null);
                // Keep blank since no API to get according to Evgeny's reply.
                storagePool.setSupportedDriveTypes(null);
                // Keep blank since no API to get according to Evgeny's reply.
                storagePool.setMaximumThinVolumeSize(null);
                // Keep blank since no API to get according to Evgeny's reply.
                storagePool.setMinimumThinVolumeSize(null);
                // Keep blank since no API to get according to Evgeny's reply.
                storagePool.setMaximumThickVolumeSize(null);
                // Keep blank since no API to get according to Evgeny's reply.
                storagePool.setMinimumThickVolumeSize(null);
                // Keep blank since no API to get according to Evgeny's reply.
                storagePool.setSupportedResourceType(StoragePool.SupportedResourceType.THIN_ONLY);
                storagePool.setPoolServiceType(StoragePool.PoolServiceType.block);
                // Keep blank since no API to get according to Evgeny's reply.
                storagePool.setCapabilities(new ArrayList<CapabilityInstance>());
                // Add the bean into result list.
                this.StoragePools.add(storagePool);
            }
            result.put("success", true);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * According to the suggestion of Evgeny, the author of the SBSDK framework, since there is no API provided by
     * Unisphere can query this information directly, query the storage port types to confirm the supported protocols.
     *
     * Currently 2 types of ports are needed:
     * 1. FC("FA-1D"),
     * 2. iSCSI("SE-2F").
     * The others are not needed.
     *
     * @param storageSystemId Storage system ID.
     * @return Supported protocols.
     */
    protected Set<StoragePool.Protocols> getSupportedProtocols(String storageSystemId) {
        String[] neededDirectorIdPrefix = DiscoverStoragePortsOperation.NEEDED_DIRECTOR_ID_PREFIX;
        Set<StoragePool.Protocols> result = new HashSet<>();
        List<String> directorIds= new SloprovisioningSymmetrixDirectorList(
            this.storageSystem.getNativeId()).perform(this.getClient());
        // Check if there is FC type port(s).
        List<String> fcTypeIds = directorIds.stream().filter(
            id -> id.startsWith(neededDirectorIdPrefix[0])).collect(Collectors.toList());
        if (fcTypeIds.size() > 0) {
            result.add(StoragePool.Protocols.FC);
        }
        // Check if there is iSCSI type port(s).
        List<String> iscsiTypeIds = directorIds.stream().filter(
            id -> id.startsWith(neededDirectorIdPrefix[1])).collect(Collectors.toList());
        if (iscsiTypeIds.size() > 0) {
            result.add(StoragePool.Protocols.iSCSI);
        }
        return result;
    }
}
