/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.operation.discovery;

import com.emc.storageos.driver.vmaxv3driver.base.OperationImpl;
import com.emc.storageos.driver.vmaxv3driver.rest.SloprovisioningSymmetrixDirectorList;
import com.emc.storageos.driver.vmaxv3driver.rest.SloprovisioningSymmetrixDirectorPortGet;
import com.emc.storageos.driver.vmaxv3driver.rest.SloprovisioningSymmetrixDirectorPortList;
import com.emc.storageos.driver.vmaxv3driver.rest.bean.SymmetrixPort;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of "DiscoverStoragePorts" operation.
 * 
 * Created by gang on 6/21/16.
 */
public class DiscoverStoragePortsOperation extends OperationImpl {

    private static final Logger logger = LoggerFactory.getLogger(DiscoverStoragePortsOperation.class);

    protected static final String[] NEEDED_DIRECTOR_ID_PREFIX = {"FA", "SE"};

    private StorageSystem storageSystem;
    private List<StoragePort> storagePorts;

    @Override
    public boolean isMatch(String name, Object... parameters) {
        if ("discoverStoragePorts".equals(name)) {
            this.storageSystem = (StorageSystem) parameters[0];
            this.storagePorts = (List<StoragePort>) parameters[1];
            if (this.storagePorts == null) {
                this.storagePorts = new ArrayList<>();
            }
            this.setClient(this.storageSystem);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Perform the storage port discovery operation. All the discovery information
     * will be set into the "StoragePorts" instance.
     *
     * @return A map indicates if the operation succeeds or fails.
     */
    @Override
    public Map<String, Object> execute() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. Get director list.
            List<String> directorIds= new SloprovisioningSymmetrixDirectorList(
                this.storageSystem.getNativeId()).perform(this.getClient());
            List<String> neededDirectorIds = this.filterDirectorIds(directorIds);
            for (String directorId : neededDirectorIds) {
                List<String> portIds = new SloprovisioningSymmetrixDirectorPortList(
                    this.storageSystem.getNativeId(), directorId).perform(this.getClient());
                for(String portId : portIds) {
                    SymmetrixPort item = new SloprovisioningSymmetrixDirectorPortGet(
                        this.storageSystem.getNativeId(), directorId, portId).perform(this.getClient());
                    StoragePort storagePort = new StoragePort();
                    storagePort.setStorageSystemId(this.storageSystem.getNativeId());
                    storagePort.setNativeId(portId);
                    storagePort.setDeviceLabel(portId);
                    storagePort.setDisplayName(portId);
                    // Need to confirm: 1. Required fields to be set, 2. How to get the values of the required fields.









                    this.storagePorts.add(storagePort);
                }
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
     * Since not all directors are needed for discovery, this method is used to
     * filter out the ones that are not needed and only return the needed directors.
     * Currently 2 types of ports are needed:
     * 1. FC("FA-1D"),
     * 2. iSCSI("SE-2F").
     * The others are not needed.
     *
     * @param directorIds The full director ID list.
     * @return The needed director ID list.
     */
    protected List<String> filterDirectorIds(List<String> directorIds) {

        List<String> result = directorIds.stream().filter(
            i -> Arrays.asList(NEEDED_DIRECTOR_ID_PREFIX).contains(i.substring(0, 2))).collect(Collectors.toList());
        return result;
    }
}
