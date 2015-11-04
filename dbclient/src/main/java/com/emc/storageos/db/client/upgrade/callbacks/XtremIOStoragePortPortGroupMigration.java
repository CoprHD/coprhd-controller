package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;

/**
 * Updates the portGroup field in XtremIO Storage ports:
 * - from DEFAULT_NAME to StorageHADomain's name
 */
public class XtremIOStoragePortPortGroupMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(XtremIOStoragePortPortGroupMigration.class);

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback#process()
     */
    @Override
    public void process() {
        log.info("START - XtremIO StoragePort PortGroup migration call back");
        DbClient dbClient = getDbClient();
        try {
            List<URI> storagePortURIs = dbClient.queryByType(StoragePort.class, true);
            Iterator<StoragePort> storagePortsIter = dbClient.queryIterativeObjects(StoragePort.class, storagePortURIs);

            // storage system object cache
            Map<URI, StorageSystem> uriToSystemMap = new HashMap<>();
            List<StoragePort> modifiedPorts = new ArrayList<>();

            while (storagePortsIter.hasNext()) {
                StoragePort storagePort = storagePortsIter.next();
                URI systemURI = storagePort.getStorageDevice();
                StorageSystem system = null;
                if (uriToSystemMap.containsKey(systemURI)) {
                    system = uriToSystemMap.get(systemURI);
                } else {
                    system = dbClient.queryObject(StorageSystem.class, systemURI);
                    if (null != system) {
                        uriToSystemMap.put(systemURI, system);
                    } else {
                        log.warn("No storage system found for the port {}, it will be skipped from updating portGroup property",
                                storagePort.getId().toString());
                        continue;
                    }
                }

                if (DiscoveredDataObject.Type.xtremio.name().equalsIgnoreCase(system.getSystemType())) {
                    if (storagePort.getStorageHADomain() != null) {
                        StorageHADomain haDomain = dbClient.queryObject(StorageHADomain.class, storagePort.getStorageHADomain());
                        if (haDomain != null) {
                            log.info("Updating the PortGroup property for the port {} with {}", storagePort.getId().toString(),
                                    haDomain.getAdapterName());
                            storagePort.setPortGroup(haDomain.getAdapterName());
                            modifiedPorts.add(storagePort);
                        }
                    } else {
                        log.warn("StorageHADomain not found on port {}, it will be skipped from updating portGroup property",
                                storagePort.getId().toString());
                    }
                }
            }

            // Persist the updated ports
            if (!modifiedPorts.isEmpty()) {
                dbClient.persistObject(modifiedPorts);
            }
        } catch (Exception e) {
            log.error("Exception occured while updating portGroup property on XtremIO storage ports", e);
        }

        log.info("END - XtremIO StoragePort PortGroup migration call back");
    }
}
