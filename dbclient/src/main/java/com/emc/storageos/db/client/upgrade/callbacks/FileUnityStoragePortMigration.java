package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
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

    private static final Logger log = LoggerFactory
            .getLogger(FileUnityStoragePortMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        this.regenerateStoragePortNativeGUIDAndPopulateIPField();
    }

    private void regenerateStoragePortNativeGUIDAndPopulateIPField() throws MigrationCallbackException {
        try {
            log.info("unity port migration: start");
            DbClient dbClient = this.getDbClient();
            List<URI> sPortList = dbClient.queryByType(StoragePort.class, true);
            Iterator<StoragePort> sPorts = dbClient.queryIterativeObjects(StoragePort.class, sPortList, true);
            List<Network> changedNetworks = new ArrayList<Network>();
            List<StoragePort> changedStoragePorts = new ArrayList<StoragePort>();
            while (sPorts.hasNext()) {
                StoragePort sPort = sPorts.next();
                log.info("Working on port: " + sPort.getLabel());
                StorageSystem system = dbClient.queryObject(StorageSystem.class, sPort.getStorageDevice());
                log.info("Storage system associated with the port: " + system.getLabel() + " " + system.getSystemType());
                log.info(sPort.getTransportType() + " " + system.getSystemType());
                if (system.getSystemType().equalsIgnoreCase("unity") && sPort.getTransportType().equalsIgnoreCase("IP")) {
                    String newNativeGUID = generateNativeGuid(system,
                            sPort.getPortGroup() + "+" + sPort.getPortNetworkId(),
                            "PORT");
                    String newIpAddress = sPort.getPortNetworkId();
                    String newPortNetworkId = system.getLabel() + ":" + sPort.getPortGroup() + ":" + sPort.getPortNetworkId();
                    log.info("new values to populate: " + newNativeGUID + " " + newIpAddress + " " + newPortNetworkId);

                    if (sPort.getNetwork() != null && !NullColumnValueGetter.isNullURI(sPort.getNetwork())) {
                        Network network = dbClient.queryObject(Network.class, sPort.getNetwork());
                        StringMap endpoints = network.getEndpointsMap();
                        if (endpoints.containsKey(sPort.getPortNetworkId())) {
                            String value = endpoints.get(sPort.getPortNetworkId());
                            endpoints.put(newPortNetworkId, value);
                            endpoints.remove(sPort.getPortNetworkId());
                            log.info("endpoint map: " + endpoints.toString());
                            network.setEndpointsMap(endpoints);
                            changedNetworks.add(network);
                        }
                    }

                    sPort.setNativeGuid(newNativeGUID);
                    sPort.setIpAddress(newIpAddress);
                    sPort.setPortNetworkId(newPortNetworkId);
                    changedStoragePorts.add(sPort);
                }
            }
            if (!changedNetworks.isEmpty()) {
                log.info("changed networks found! persisting");
                dbClient.updateObject(changedNetworks);
            }
            if (!changedStoragePorts.isEmpty()) {
                log.info("changed ports found! persisting");
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