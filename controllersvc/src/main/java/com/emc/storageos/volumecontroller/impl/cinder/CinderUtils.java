/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.cinder;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.cinder.CinderEndPointInfo;
import com.emc.storageos.cinder.api.CinderApi;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StorageProvider.ConnectionStatus;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

public class CinderUtils
{

    private static final Logger _log = LoggerFactory.getLogger(CinderUtils.class);

    private static final Integer timeout = 10000;           // in milliseconds
    private static final Integer connectTimeout = 10000;    // in milliseconds
    private static final String STRICT_HOST_KEY_CHECKING = "StrictHostKeyChecking";
    private static final String NO = "no";

    public static void updateStoragePoolCapacity(DbClient dbClient,
            CinderApi client,
            StoragePool storagePool,
            String capacityInGB,
            boolean isSubstract)
    {
        StorageSystem storageSystem = null;
        try
        {
            storageSystem = dbClient.queryObject(StorageSystem.class, storagePool.getStorageDevice());
            _log.info(String.format("Old storage pool capacity data for \n"
                    + "  pool %s/%s --- \n  free capacity: %s; "
                    + "subscribed capacity: %s",
                    storageSystem.getId(), storagePool.getId(),
                    storagePool.calculateFreeCapacityWithoutReservations(),
                    storagePool.getSubscribedCapacity()));

            // Update storage pool capacity and save to data base
            long sizeInGB = Long.parseLong(capacityInGB);
            long currentSubscribedCapacity = storagePool.getSubscribedCapacity();
            long currentFreeCapacity = storagePool.getFreeCapacity();

            if (isSubstract)
            { // For delete case
                storagePool.setFreeCapacity(currentFreeCapacity + sizeInGB * 1024 * 1024); // KBytes - KBytes
                storagePool.setSubscribedCapacity(currentSubscribedCapacity - sizeInGB * 1024 * 1024); // KBytes + KBytes
            }
            else
            {// For create case
                storagePool.setFreeCapacity(currentFreeCapacity - (sizeInGB * 1024 * 1024)); // KBytes - KBytes
                long newSubscribedCapacity = currentSubscribedCapacity + (sizeInGB * 1024 * 1024);// KBytes + KBytes
                storagePool.setSubscribedCapacity(newSubscribedCapacity); 

                // Check if total capacity needs an adjustment
                if (isCapacityLimitExceeded((double)newSubscribedCapacity, (double)storagePool.getTotalCapacity()))
                {
                    // If 75% mark is reached for consumed percent, then increment the total capacity by 2 times
                    Long currentTotalCapacity = storagePool.getTotalCapacity();
                    long newTotalCapacity = currentTotalCapacity * 2;
                    storagePool.setTotalCapacity(newTotalCapacity);
                    storagePool.setFreeCapacity(newTotalCapacity - newSubscribedCapacity);
                    _log.info(String.format("Consumed capacity perecent was greater than or equal to 75 percent \n"
                            + " Hence, increased the total capacity by 2 times"
                            + " New total capacity is %s",
                            newTotalCapacity));
                }
            }

            _log.info(String.format("New storage pool capacity data for pool \n"
                    + "  %s/%s --- \n  free capacity: %s;"
                    + " subscribed capacity: %s",
                    storageSystem.getId(), storagePool.getId(),
                    storagePool.getFreeCapacity(),
                    storagePool.getSubscribedCapacity()));

            dbClient.persistObject(storagePool);
        } catch (Exception ex)
        {
            _log.error(
                    String.format("Failed to update capacity of storage pool after volume provisioning operation."
                            + "%n  Storage system: %s, storage pool %s .",
                            storageSystem.getId(), storagePool.getId()), ex);
        }

    }

    /**
     * This method checks if the used capacity is 75% or more.
     * 
     * @param storagePool
     * @return
     */
    private static boolean isCapacityLimitExceeded(double usedCapacity, double totalCapacity)
    {
        double consumedPercent = (usedCapacity / totalCapacity) * 100;
        if (consumedPercent >= 75) {
            return true;
        }

        return false;

    }

    /**
     * Gets the cinder endpoint info to access the endpoint
     * 
     * @param storageProviderURi
     * @return
     */
    public static CinderEndPointInfo getCinderEndPoint(URI storageProviderURi, DbClient dbClient)
    {
        StorageProvider provider = dbClient.queryObject(StorageProvider.class, storageProviderURi);

        // Get the persisted end point info
        StringMap endPointKeys = provider.getKeys();

        String hostName = endPointKeys.get(CinderConstants.KEY_CINDER_HOST_NAME);
        String password = endPointKeys.get(CinderConstants.KEY_CINDER_REST_PASSWORD);
        String userName = endPointKeys.get(CinderConstants.KEY_CINDER_REST_USER);
        String tenantName = endPointKeys.get(CinderConstants.KEY_CINDER_TENANT_NAME);
        String tenantId = endPointKeys.get(CinderConstants.KEY_CINDER_TENANT_ID);
        String baseUri = endPointKeys.get(CinderConstants.KEY_CINDER_REST_URI_BASE);
        String token = endPointKeys.get(CinderConstants.KEY_CINDER_REST_TOKEN);

        CinderEndPointInfo ep = new CinderEndPointInfo(hostName, userName, password, tenantName);
        if (baseUri.startsWith(CinderConstants.HTTP_URL))
        {
            ep.setCinderBaseUriHttp(baseUri);
        }
        else
        {
            ep.setCinderBaseUriHttps(baseUri);
        }

        ep.setCinderToken(token);
        ep.setCinderTenantId(tenantId);

        return ep;
    }

    /**
     * Refresh cinder connections.
     * 
     * @param cinderProviderList the cinder provider list
     * @param dbClient the db client
     * @return the list
     */
    public static List<URI> refreshCinderConnections(final List<StorageProvider> cinderProviderList,
            DbClient dbClient) {
        List<URI> activeProviders = new ArrayList<URI>();
        for (StorageProvider storageProvider : cinderProviderList) {
            try {
                // Makes sure Cinder Provider is reachable
                checkProviderConnection(storageProvider);
                storageProvider.setConnectionStatus(ConnectionStatus.CONNECTED.name());
                activeProviders.add(storageProvider.getId());
                _log.info("Storage Provider {} is reachable", storageProvider.getIPAddress());
            } catch (Exception e) {
                storageProvider.setConnectionStatus(ConnectionStatus.NOTCONNECTED.name());
                _log.error("Storage Provider {} is not reachable", storageProvider.getIPAddress());
            } finally {
                dbClient.persistObject(storageProvider);
            }
        }
        return activeProviders;
    }

    private static void checkProviderConnection(StorageProvider storageProvider)
            throws JSchException, SftpException, IOException {
        ChannelSftp sftp = null;
        Session session = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(storageProvider.getUserName(),
                    storageProvider.getIPAddress(),
                    storageProvider.getPortNumber());
            session.setPassword(storageProvider.getPassword());
            Hashtable<String, String> config = new Hashtable<String, String>();
            config.put(STRICT_HOST_KEY_CHECKING, NO);
            session.setConfig(config);
            session.connect(timeout);
            _log.debug("Session Connected...");
            Channel channel = session.openChannel("sftp");
            sftp = (ChannelSftp) channel;
            sftp.connect(connectTimeout);
        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    /**
     * Creates StorageHADomain for storage port.
     * 
     * Cinder API does not provide the information about the storage adapter.
     * Consider it as a dummy storageHADomain for cinder based systems. If the storageHADomain
     * is not populated then the port will not be considered for multipath participatipation. While
     * creating a virtual pool with multiple paths, it gets discarded if storageHADomain is not found.
     * 
     * @param storageSystem
     * @param dbClient
     * @return
     */
    public static StorageHADomain getStorageAdapter(StorageSystem storageSystem, DbClient dbClient)
    {

        String cinderHostName = "";
        URI providerUri = storageSystem.getActiveProviderURI();
        StorageProvider provider = dbClient.queryObject(StorageProvider.class, providerUri);
        if (null != provider && null != provider.getKeys())
        {
            cinderHostName = provider.getKeyValue(CinderConstants.KEY_CINDER_HOST_NAME);
        }

        String adapterNativeGUID = NativeGUIDGenerator.generateNativeGuid(storageSystem,
                cinderHostName, NativeGUIDGenerator.ADAPTER);
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
}
