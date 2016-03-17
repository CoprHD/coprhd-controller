/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.monitoring;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.ConnectionManagerException;
import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.StorageProvider.ConnectionStatus;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.netflix.astyanax.connectionpool.ConnectionFactory;

/**
 * Handles monitoring for vnxFile device indications
 */
public class VNXFileMonitoringImpl implements IMonitoringStorageSystem {

    private final Logger _logger = LoggerFactory.getLogger(VNXFileMonitoringImpl.class);

    private DbClient _dbClient;
    private CIMConnectionFactory _connectionFactory;
    /**
     * Lock instance to handle static CACHE synchronization
     */
    private final Object cacheLock = new Object();

    /**
     * Holds list of unique vnxfile URIs managed by this local bourne node and its callback instances.
     * 1. Key -> vnxFile URI
     * 2. Value -> callback instance of the monitoringJob lock
     */
    private final Map<String, DistributedQueueItemProcessedCallback> VNXFILE_CACHE =
            new ConcurrentHashMap<String, DistributedQueueItemProcessedCallback>();

    /**
     * Holds list of unique vnxFile URI failed subscription. So that scheduler can try subscription in the next cycle.
     */
    private final Set<String> FAILED_VNXFILE_SUBSCRIPTION = Collections.synchronizedSet(new HashSet<String>());

    /**
     * Takes care monitoring for the given vnxfile immediately after acquiring monitoringJob token from zookeeper queue.
     */
    @Override
    public void startMonitoring(MonitoringJob monitoringJob,
            DistributedQueueItemProcessedCallback callback) {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        synchronized (cacheLock) {
            _logger.info("monitoringJob.getId() {}", monitoringJob.getId());

            String storageSystemURI = monitoringJob.getId().toString();
            addVNXFileStorageSystemIntoCache(storageSystemURI, callback);
            /**
             * Delete existing stale subscriptions and make new subscription
             */
            boolean isSuccess = makeVNXFileSubscription(storageSystemURI);

            if (!isSuccess) {
                _logger.debug("VNX File:{} subscription is not successful.", storageSystemURI);
                addVNXFailedSubscription(storageSystemURI);
            } else {
                _logger.info("Successfully subscribed with vnxfile's smi-s provider for indication");
            }
        }
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());

    }

    /**
     * Scheduled action for the vnxfile monitoring.
     * 1. Removes Stale vnxFile from cache
     * 2. Makes new subscription to the failed subscription happened on last schedule cycle.
     */
    @Override
    public void scheduledMonitoring() {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        synchronized (cacheLock) {
            try {
                stopMonitoringStaleSystem();
                handleVNXFileSubscription();
            } catch (IOException e) {
                _logger.error(e.getMessage(), e);
            } catch (ConnectionManagerException e) {
                _logger.error(e.getMessage(), e);
            }
        }
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    /**
     * 1. Find stale vnxFile devices.
     * 2. UNsubscribe existing subscription to avoid indications for the stale vnx devices.
     * 3. Clear stale devices from zoo keeper queue and local CACHE.
     */
    @Override
    public void stopMonitoringStaleSystem() {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        Iterator<Map.Entry<String, DistributedQueueItemProcessedCallback>> iter = VNXFILE_CACHE.entrySet().iterator();
        StorageSystem storageDevice = null;
        while (iter.hasNext()) {
            Map.Entry<String, DistributedQueueItemProcessedCallback> entry = iter.next();
            String storageDeviceURI = entry.getKey();
            _logger.debug("storageDeviceURI :{}", storageDeviceURI);
            try {
                storageDevice = _dbClient.queryObject(StorageSystem.class, URI.create(storageDeviceURI));
            } catch (DatabaseException e) {
                _logger.error(e.getMessage(), e);
            }

            if (null == storageDevice || storageDevice.getInactive()) {
                _logger.info("Stale vnxfiler {} has been removed from monitoring", storageDeviceURI);
                _connectionFactory.unsubscribeSMIProviderConnection(storageDeviceURI);
                try {
                    entry.getValue().itemProcessed();// Removes monitorinJob token from queue
                } catch (Exception e) {
                    _logger.error("Exception occurred while removing monitoringJob token from ZooKeeper queue", e);
                } finally {
                    iter.remove();// Removes from local CACHE
                    FAILED_VNXFILE_SUBSCRIPTION.remove(storageDeviceURI);
                }
            }
        }
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());

    }

    /**
     * 1. Refresh all vnxfile connections.
     * 2. Make new subscription for the failed one in last schedule cycle.
     * 
     * @throws IOException
     * @throws ConnectionManagerException
     */
    private void handleVNXFileSubscription() throws IOException, ConnectionManagerException {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        _connectionFactory.refreshVnXFileConnections();
        Iterator<String> failedVnxSubscriptionIter = FAILED_VNXFILE_SUBSCRIPTION.iterator();
        while (failedVnxSubscriptionIter.hasNext()) {
            String storageSystemURI = failedVnxSubscriptionIter.next();

            if (makeVNXFileSubscription(storageSystemURI)) {
                failedVnxSubscriptionIter.remove();
            } else {
                _logger.debug("Failed to make new subscription for the {}. " +
                        "This will be taking care in the next monitoring cycle", storageSystemURI);
            }
        }
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    /**
     * 1. Delete existing stale subscription.
     * 2. Make new subscription for monitoring indication
     * 
     * @param storageSystemURI
     * @return status. true if it success.
     */
    public boolean makeVNXFileSubscription(String storageSystemURI) {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        boolean isSuccess = false;

        // Check for Storage System compatibility & SMIS provider connection status
        if (isCompatibleDevice(storageSystemURI) &&
                isSMISProviderConnected(storageSystemURI)) {
            /**
             * Delete stale subscriptions
             */
            _logger.debug("Storage System(vnx file) delete stale subscription status: {}"
                    , _connectionFactory.deleteVnxFileStaleSubscriptions(storageSystemURI));
            isSuccess = _connectionFactory.subscribeVnxFileForIndication(storageSystemURI);
            _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        }

        return isSuccess;
    }

    /**
     * Returns true for the COMPATABLIE/UNKNOWN status
     * Returns false for the other
     * 
     * @param storageSystemURI
     * @return {@link Boolean}
     */
    private Boolean isCompatibleDevice(String storageSystemURI) {
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, URI.create(storageSystemURI));
        if (CompatibilityStatus.COMPATIBLE.name().equalsIgnoreCase(storageSystem.getCompatibilityStatus())
                || CompatibilityStatus.UNKNOWN.name().equalsIgnoreCase(storageSystem.getCompatibilityStatus())) {
            return true;
        } else {
            _logger.info("Subscription will not initiated for the incompatible storage device :{}", storageSystemURI);
            return false;
        }
    }

    /**
     * Returns true if the storage provider is in CONNECTED state
     * Returns false otherwise
     * 
     * @param storageSystemURI
     * @return
     */
    private Boolean isSMISProviderConnected(String storageSystemURI) {
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, URI.create(storageSystemURI));
        if (null != storageSystem.getSmisConnectionStatus() &&
                ConnectionStatus.CONNECTED.toString().equalsIgnoreCase(
                        storageSystem.getSmisConnectionStatus())) {
            return true;
        } else {
            _logger.info("Subscription will not be initiated for storage device {} as the storage provider is in NOT_CONNECTED state",
                    storageSystemURI);
            return false;
        }
    }

    /**
     * Adds vnxFile's URI and callback instance into CACHE
     * 
     * @param storageSystemURI
     * @param callBack
     */
    private void addVNXFileStorageSystemIntoCache(String storageSystemURI, DistributedQueueItemProcessedCallback callBack) {
        if (StringUtils.isNotEmpty(storageSystemURI)) {
            VNXFILE_CACHE.put(storageSystemURI, callBack);
        }
    }

    /**
     * Adds vnxFile's URI into failed CACHE. SO that subscription will happen through scheduled monitoring
     * 
     * @param storageSystemURI
     */
    private void addVNXFailedSubscription(String storageSystemURI) {
        if (StringUtils.isNotEmpty(storageSystemURI)) {
            FAILED_VNXFILE_SUBSCRIPTION.add(storageSystemURI);
        }
    }

    /**
     * Setter method for {@link ConnectionFactory} instance
     * 
     * @param _connectionFactory {@link ConnectionFactory}
     */
    public void setConnectionFactory(CIMConnectionFactory connectionFactory) {
        this._connectionFactory = connectionFactory;
    }

    /**
     * Setter method for DbClient instance
     * 
     * @param _dbClient {@link DbClient}
     */
    public void setDbClient(DbClient dbClient) {
        this._dbClient = dbClient;
    }

    @Override
    public void clearCache() {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        synchronized (cacheLock) {
            VNXFILE_CACHE.clear();
            FAILED_VNXFILE_SUBSCRIPTION.clear();
        }
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

}
