/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

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
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * DB Migration callback to populate storageHADomain attribute for
 * pre-existing storage ports.
 * 
 */
public class StoragePortHADomainPopulater extends BaseCustomMigrationCallback
{

    private static final Logger log = LoggerFactory.getLogger(StoragePortHADomainPopulater.class);

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback#process()
     */
    @Override
    public void process() throws MigrationCallbackException
    {
        log.info("START - StoragePortHADomainPopulater migration call back");
        DbClient dbClient = getDbClient();
        List<URI> storagePortURIs = dbClient.queryByType(StoragePort.class, false);
        Iterator<StoragePort> storagePortsIter = dbClient.queryIterativeObjects(StoragePort.class, storagePortURIs);

        Map<URI, StorageSystem> uriVsSystem = new HashMap<>();
        List<StoragePort> modifiedPorts = new ArrayList<>();

        while (storagePortsIter.hasNext())
        {
            StoragePort storagePort = storagePortsIter.next();
            URI deviceURI = storagePort.getStorageDevice();
            StorageSystem system = null;

            if (uriVsSystem.containsKey(deviceURI))
            {
                system = uriVsSystem.get(deviceURI);
            }
            else
            {
                system = dbClient.queryObject(StorageSystem.class, deviceURI);
                if (null != system)
                {
                    uriVsSystem.put(deviceURI, system);
                }
                else
                {
                    log.warn("No storage system found for the port - {} , it will be skipped from"
                            + "migration, storageHADomain will continue to remain empty", storagePort.getId().toString());
                    continue;
                }

            }

            if (storagePort.getStorageHADomain() == null &&
                    DiscoveredDataObject.Type.openstack.name().equalsIgnoreCase((system.getSystemType())))
            {
                StorageHADomain haDomain = createStorageAdapter(system, dbClient);
                log.info("Populating the storageHADomian for the port - {} ", storagePort.getId().toString());
                storagePort.setStorageHADomain(haDomain.getId());
                modifiedPorts.add(storagePort);
            }

        }

        // Persist the change
        if (!modifiedPorts.isEmpty())
        {
            dbClient.persistObject(modifiedPorts);
        }

        log.info("END - StoragePortHADomainPopulater migration call back");
    }

    private StorageHADomain createStorageAdapter(StorageSystem storageSystem,
            DbClient dbClient) {

        String cinderHostName = "";
        URI providerUri = storageSystem.getActiveProviderURI();
        StorageProvider provider = dbClient.queryObject(StorageProvider.class, providerUri);
        if (null != provider && null != provider.getKeys())
        {
            cinderHostName = provider.getKeyValue("CINDER_HOST_NAME");
        }

        String adapterNativeGUID = generateNativeGuid(storageSystem, cinderHostName, "ADAPTER");
        StorageHADomain adapter = new StorageHADomain();
        adapter.setStorageDeviceURI(storageSystem.getId());
        adapter.setId(URIUtil.createId(StorageHADomain.class));
        adapter.setAdapterName(cinderHostName);
        adapter.setLabel(cinderHostName);
        adapter.setNativeGuid(adapterNativeGUID);
        adapter.setNumberofPorts("1");
        adapter.setAdapterType(StorageHADomain.HADomainType.FRONTEND.name());
        adapter.setInactive(false);
        dbClient.createObject(adapter);

        return adapter;
    }

    private String generateNativeGuid(StorageSystem device, String uniqueId, String type)
    {
        String typeStr = type;
        return String.format("%s+%s+%s+%s", "OPENSTACK", device.getSerialNumber(), typeStr, uniqueId);
    }
}
