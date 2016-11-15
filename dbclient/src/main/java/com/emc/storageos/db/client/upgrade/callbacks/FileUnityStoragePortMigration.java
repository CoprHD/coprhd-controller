package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualPool.SystemType;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * 
 * Migration of DELL EMC Unity StoragePorts using a new format to ensure uniqueness in case of Replicated nas servers
 * 
 * Old NativeGUID : systemtype+serial+PORT+IP
 * new NativeGUID : systemtype+serial+PORT+nasId+IP
 * 
 * Old portNetworkId : IP
 * New portNetworkId : system:nasId:IP
 * 
 * populating IP address field with the IP of the nas server
 * 
 * @author yelkaa
 *
 */

public class FileUnityStoragePortMigration extends BaseCustomMigrationCallback {

    @Override
    public void process() throws MigrationCallbackException {
        this.regenerateStoragePortNativeGUIDAndPopulateIPField();
    }

    private void regenerateStoragePortNativeGUIDAndPopulateIPField() throws MigrationCallbackException {
        try {
            DbClient dbClient = this.getDbClient();
            List<URI> sPortList = dbClient.queryByType(StoragePort.class, true);
            Iterator<StoragePort> sPorts = dbClient.queryIterativeObjects(StoragePort.class, sPortList, true);
            List<Network> changedNetworks = new ArrayList<Network>();
            List<StoragePort> changedStoragePorts = new ArrayList<StoragePort>();
            while (sPorts.hasNext()) {
                StoragePort sPort = sPorts.next();
                StorageSystem system = dbClient.queryObject(StorageSystem.class, sPort.getStorageDevice());
                if (system.getSystemType().equalsIgnoreCase(SystemType.unity.name()) && sPort.getTransportType().equalsIgnoreCase("IP")) {
                    String newNativeGUID = generateNativeGuid(system,
                            sPort.getPortGroup() + "+" + sPort.getPortNetworkId(),
                            "PORT");
                    String newIpAddress = sPort.getPortNetworkId();
                    String newPortNetworkId = system.getLabel() + ":" + sPort.getPortGroup() + ":" + sPort.getPortNetworkId();

                    sPort.setNativeGuid(newNativeGUID);
                    sPort.setIpAddress(newIpAddress);
                    sPort.setPortNetworkId(newPortNetworkId);
                    changedStoragePorts.add(sPort);

                    Network network = dbClient.queryObject(Network.class, sPort.getNetwork());
                    StringMap endpoints = network.getEndpointsMap();
                    if (endpoints.containsKey(sPort.getPortNetworkId())) {
                        String value = endpoints.get(sPort.getPortNetworkId());
                        endpoints.remove(sPort.getPortNetworkId());
                        endpoints.put(newPortNetworkId, value);
                        network.setEndpointsMap(endpoints);
                        changedNetworks.add(network);
                    }

                }
            }
            if (!changedNetworks.isEmpty()) {
                dbClient.updateObject(changedNetworks);
            }
            if (!changedStoragePorts.isEmpty()) {
                dbClient.updateObject(changedStoragePorts);
            }

        } catch (Exception e) {
            String errorMsg = String.format("%s encountered unexpected error %s", this.getName(), e.getMessage());
            throw new MigrationCallbackException(errorMsg, e);
        }
    }

    public String generateNativeGuid(StorageSystem device, String uniqueId, String type) {
        return String.format("%s+%s+%s+%s", "UNITY", device.getSerialNumber(), type, uniqueId);
    }
}