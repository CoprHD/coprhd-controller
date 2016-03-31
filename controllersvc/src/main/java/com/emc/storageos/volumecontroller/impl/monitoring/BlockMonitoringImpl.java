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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.google.common.collect.Sets;
import com.netflix.astyanax.connectionpool.ConnectionFactory;

/**
 * Takes care monitoring for vmax and vnxblock storage devices.
 */
public class BlockMonitoringImpl implements IMonitoringStorageSystem {

    private final Logger _logger = LoggerFactory.getLogger(BlockMonitoringImpl.class);

    /**
     * Holds List of SMIS Provider's URI which are managed by this Bourne Node and its callBack instance
     * Key : SMIS Provider's URI
     * Value: Callback instance of the MonitoringJob available on ZooKeeper
     */
    private final Map<String, DistributedQueueItemProcessedCallback> SMIS_PROVIDERS_CACHE =
            new ConcurrentHashMap<String, DistributedQueueItemProcessedCallback>();
    /**
     * Holds unique Active SMIS Providers URI managed by this Bourne Node.
     */
    private final Set<String> ACTIVE_SMIS_PROVIDERS_CACHE = Collections.synchronizedSet(new HashSet<String>());
    /**
     * Lock instance to handle static CACHE synchronization
     */
    private final Object cacheLock = new Object();

    private DbClient _dbClient;
    private CIMConnectionFactory _connectionFactory;

    /**
     * 1.Add SMIS Provider's URI into SMIS_PROVIDERS_CACHE. This is needed to check active passive status changes.
     * 2. Check the given smis provider is active or not.
     * a. If the given smisprovider is an active, make new subscription for indication
     * 
     * @param monitoringJob {@link MonitoringJob} moniotoringJob item
     * @param callback {@link DistributedQueueItemProcessedCallback} callback instance
     * @throws IOException IOException
     */
    @Override
    public void startMonitoring(MonitoringJob monitoringJob,
            DistributedQueueItemProcessedCallback callback) {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        try {
            synchronized (cacheLock) {
                URI smisProviderURI = monitoringJob.getId();

                // Add SMIS provider's URI in to SMIS_PROVIDERS_CACHE
                String smisProvider = smisProviderURI.toString();
                _logger.info("smisProvider :{}", smisProvider);
                addSMISProviderIntoAllProviderCache(smisProvider, callback);

                // Delete stale subscriptions
                _logger.debug("SMI-S Provider delete stale subscription status: {}"
                        , _connectionFactory.deleteStaleSubscriptions(smisProvider));
                // Creates new subscription if it is a active SMIS provider
                if (isActiveSMISProvider(smisProvider)) {
                    _logger.info("SMIS Provider {} is an active provider", smisProviderURI);
                    boolean successStatus = _connectionFactory.subscribeSMIProviderConnection(smisProvider);
                    if (successStatus) {
                        // Add SMIS provider's URI in to ACTIVE_SMIS_PROVIDERS_CACHE
                        addSMISProviderIntoActiveProviderCache(smisProvider);
                        _logger.info("Added SMIS Provider {} into Active SMIS provider cache", smisProviderURI);
                    } else {
                        _logger.info("Subscription for the new Active SMIS Provider {} is failed. " +
                                "Scheduled Job will try to make subscription in the next cycle");
                    }
                } else {
                    _logger.info("SMIS provider {} is Passive provider, so no need to make subscription for indication now",
                            smisProviderURI);
                }
            }
        } catch (IOException e) {
            _logger.error(e.getMessage(), e);
        }
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());

    }

    /**
     * Periodically checks Active/Passive changes on SMIS provider.
     * If passive became active, do subscription for indication.
     * If active becomes passive, do un-subscription to avoid duplicate indications.
     */
    @Override
    public void scheduledMonitoring() {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        try {
            synchronized (cacheLock) {
                stopMonitoringStaleSystem();
                handleActivePassiveMonitoringChanges();
            }
        } catch (Exception e) {
            _logger.error(e.getMessage(), e);
        }
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    /**
     * 1.Find stale SMIS Provider from DB
     * 2. Un-subscribe cimconnection to avoid indications.
     * 3. Remove stale smisprovider URI from local CACHE
     */
    @Override
    public void stopMonitoringStaleSystem() {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        Iterator<Map.Entry<String, DistributedQueueItemProcessedCallback>> iter = SMIS_PROVIDERS_CACHE.entrySet().iterator();
        StorageProvider smisprovider = null;
        while (iter.hasNext()) {
            Map.Entry<String, DistributedQueueItemProcessedCallback> entry = iter.next();
            String smisProvoiderURI = entry.getKey();
            _logger.debug("smisProvoiderURI :{}", smisProvoiderURI);
            try {
                smisprovider = _dbClient.queryObject(StorageProvider.class, URI.create(smisProvoiderURI));
            } catch (final DatabaseException e) {
                _logger.error(e.getMessage(), e);
            }

            if (null == smisprovider || smisprovider.getInactive()) {
                _logger.info("Stale SMIS Provider {} has been removed from monitoring", smisProvoiderURI);
                _connectionFactory.unsubscribeSMIProviderConnection(smisProvoiderURI);
                try {
                    entry.getValue().itemProcessed();// Removes monitorinJob token from queue
                } catch (Exception e) {
                    _logger.error("Exception occurred while removing monitoringJob token from ZooKeeper queue", e);
                } finally {
                    iter.remove();// Removes from CACHE
                    ACTIVE_SMIS_PROVIDERS_CACHE.remove(smisProvoiderURI);
                }
            }
        }
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    /**
     * Creates new subscriptions to new active providers(SMIS providers got changed from Active to Passive) managed by this controller.
     * Clears existing subscriptions to new passive providers(SMIS providers got changed from Passive to Active managed by this controller
     * to avoid indications from Passive Providers.
     * Work flow steps:
     * 1.Get all active SMIS Providers from DB.
     * 2.Filter active SMIS providers managed by other controller
     * 3.Find the new active Providers change set and make subscription for indication.
     * 4.Find the new Passive Providers change set and un-subscribe for indication to avoid indications from Passive providers.
     * 
     * For Example: This Bourne node takes care of Providers P1, P2, P3 and P4.
     * Active Providers at last scheduled time were: P1 and P2
     * Passive Provider at last scheduled time were: P3 and P4.
     * 
     * Now P2 became Passive from Active and P3 became Active from Passive.
     * 
     * Before starting this method SMIS_PROVIDERS_CACHE = { P1, P2, P3 and P4} and ACTIVE_SMIS_PROVIDERS_CACHE = { P1 and P2}
     * 
     * This method will find out new Active and Passive change set.
     * Active Change Set = {P3} as P3 became Active from Passive in this cycle.
     * Passive Change Set = {P2} as P2 became Passive from Active in this cycle.
     * 
     * This node has to make new subscription to only P3 as only P3 became Active from Passive.
     * This node has to unsubscribe connection from P2 as P2 became Passive from Active.
     * 
     * No need any special action for P1 and P4 as these two node's state(active/passive) did not change in this cycle.
     * 
     * 
     */
    protected void handleActivePassiveMonitoringChanges() throws Exception {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        _logger.debug("SMIS_PROVIDERS_CACHE :{}", SMIS_PROVIDERS_CACHE);
        _logger.debug("ACTIVE_SMIS_PROVIDERS_CACHE :{}", ACTIVE_SMIS_PROVIDERS_CACHE);
        Set<String> allActiveSMISProvidersFromDB = getAllActiveSMISProviderFromDB();

        /**
         * Filter active SMIS providers managed by other controllers.
         * In this example allActiveSMISProvidersFromDB will have other SMIS provider like P5.
         * So we need to filter SMIS providers managed by other controller.
         * This node should takes care only P1, P2, P3 and P4 as it got locks for P1, P2, P3 and P4.
         * 
         */
        Set<String> activeProvidersManagedByThisNodeFromDB = Sets.intersection(SMIS_PROVIDERS_CACHE.keySet(), allActiveSMISProvidersFromDB);
        _logger.debug("activeProvidersManagedByThisNodeFromDB :{}", activeProvidersManagedByThisNodeFromDB);
        /**
         * Find new active provider change set to make new subscription
         * activeProvidersManagedByThisNodeFromDB = { P1 and P3}
         * ACTIVE_SMIS_PROVIDERS_CACHE = {P1 and P2}
         * activeProvidersChangeSet = {P3}
         * The returned set contains all elements that are contained by activeProvidersManagedByThisNodeFromDB and not contained by
         * ACTIVE_SMIS_PROVIDERS_CACHE.
         */

        Set<String> activeProvidersChangeSet = new HashSet<String>();
        Sets.difference(activeProvidersManagedByThisNodeFromDB, ACTIVE_SMIS_PROVIDERS_CACHE).copyInto(activeProvidersChangeSet);
        _logger.debug("activeProvidersChangeSet :{}", activeProvidersChangeSet);
        startSubscriptionForMonitoring(activeProvidersChangeSet);

        /**
         * Find new passive provider change set to un-subscribe existing subscription
         * ACTIVE_SMIS_PROVIDERS_CACHE = {P1, P2 and P3}
         * activeProvidersManagedByThisNodeFromDB = {P1 and P3}
         * passiveProvidersChangeSet = {P2}
         */

        Set<String> passiveProvidersChangeSet = new HashSet<String>();
        Sets.difference(ACTIVE_SMIS_PROVIDERS_CACHE, activeProvidersManagedByThisNodeFromDB).copyInto(passiveProvidersChangeSet);
        _logger.debug("passiveProvidersChangeSet :{}", passiveProvidersChangeSet);
        startUnsubscriptionForMonitoring(passiveProvidersChangeSet);

        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());

    }

    /**
     * Clears existing subscription for the given list of unique passive SMIS providers for Monitoring
     * 
     * @param passiveProvidersChangeSet {@link Set} Passive provider's URIs to unsubscribe existing connection for monitoring
     */
    private void startUnsubscriptionForMonitoring(
            Set<String> passiveProvidersChangeSet) {
        for (String smisProviderUri : passiveProvidersChangeSet) {

            if (_connectionFactory.unsubscribeSMIProviderConnection(smisProviderUri)) {
                ACTIVE_SMIS_PROVIDERS_CACHE.remove(smisProviderUri);
                _logger.info("Cleared existing subscription for the passive SMI-S Provider :{}", smisProviderUri);
            } else {
                _logger.error("Un Subscription to the passive SMIS provider {} is failed. " +
                        "Controller will try to un-subscribe in the next scheduled cycle", smisProviderUri);
            }

        }
    }

    /**
     * Makes new subscription for the give list of unique Active SMIS Providers for Monitoring.
     * 
     * @param activeProvidersChangeSet {@link Set} Active provider's URIs to make new subscription for monitoring.
     */
    private void startSubscriptionForMonitoring(
            Set<String> activeProvidersChangeSet) {
        for (String smisProviderUri : activeProvidersChangeSet) {
            boolean isSuccess = _connectionFactory.subscribeSMIProviderConnection(smisProviderUri);
            if (isSuccess) {
                ACTIVE_SMIS_PROVIDERS_CACHE.add(smisProviderUri);
                _logger.info("Created new subscription for the active SMI-S Provider :{}", smisProviderUri);
            } else {
                _logger.error("Subscription to the active SMIS provider {} is failed. " +
                        "Controller will try to make new subscription in the next scheduled cycle", smisProviderUri);
            }
        }

    }

    /**
     * Get all Active SMIS provider's URI from DB.
     * 
     * @return {@link Set} Unique Active SMIS Providers URI.
     * @throws DatabaseException DatabaseException
     */
    public Set<String> getAllActiveSMISProviderFromDB() throws DatabaseException {
        _logger.debug("Entering {}",
                Thread.currentThread().getStackTrace()[1].getMethodName());

        Set<String> allActiveSMISProvidersFromDB = new HashSet<String>();
        List<URI> allStorageSystemsURIList = _dbClient.queryByType(StorageSystem.class, true);
        Iterator<StorageSystem> allStorageSystemIter = _dbClient.queryIterativeObjects(StorageSystem.class, allStorageSystemsURIList);
        while (allStorageSystemIter.hasNext()) {
            StorageSystem storageSystem = allStorageSystemIter.next();
            _logger.debug("storageSystem.getDeviceType() :{}", storageSystem.getSystemType());
            _logger.debug("storageSystem.getActiveProviderURI() :{}", storageSystem.getActiveProviderURI());

            if ((CompatibilityStatus.COMPATIBLE.name().equalsIgnoreCase(storageSystem.getCompatibilityStatus())
                    || CompatibilityStatus.UNKNOWN.name().equalsIgnoreCase(storageSystem.getCompatibilityStatus()))
                    && Type.isProviderStorageSystem(storageSystem.getSystemType())
                    && null != storageSystem.getActiveProviderURI() && !storageSystem.getInactive()
                    && !NullColumnValueGetter.getNullURI().equals(
                            storageSystem.getActiveProviderURI())) {
                allActiveSMISProvidersFromDB.add(storageSystem
                        .getActiveProviderURI().toString());
            }
        }
        _logger.debug("Active SMIS Providers URI:{}",
                allActiveSMISProvidersFromDB);
        _logger.debug("Exiting {}",
                Thread.currentThread().getStackTrace()[1].getMethodName());
        return allActiveSMISProvidersFromDB;
    }

    /**
     * Checks the given SMIS provider is active or Passive
     * 
     * @param smisProviderURI SMIS Provider's URI
     * @return True if the given SMIS Provider is Active, else Returns false.
     * @throws IOException IOException
     */
    private boolean isActiveSMISProvider(String smisProviderURI) throws IOException {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        Set<String> activeSMISProvidersFromDB = getAllActiveSMISProviderFromDB();
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        return activeSMISProvidersFromDB.contains(smisProviderURI);
    }

    private void addSMISProviderIntoAllProviderCache(String smisProviderURI, DistributedQueueItemProcessedCallback callBack) {
        if (StringUtils.isNotEmpty(smisProviderURI) && !NullColumnValueGetter.getNullStr().equalsIgnoreCase(smisProviderURI)) {
            SMIS_PROVIDERS_CACHE.put(smisProviderURI, callBack);
        }
    }

    /**
     * Adds given SMIS provider's URI in to ACTIVE_SMIS_PROVIDERS_CACHE
     * 
     * @param smisProviderURI {@link String} SMIS Provider's URI
     */
    private void addSMISProviderIntoActiveProviderCache(String smisProviderURI) {
        if (StringUtils.isNotEmpty(smisProviderURI)) {
            ACTIVE_SMIS_PROVIDERS_CACHE.add(smisProviderURI);
        }
    }

    /**
     * Setter method for DbClient instance
     * 
     * @param _dbClient {@link DbClient}
     */
    public void setDbClient(DbClient dbClient) {
        this._dbClient = dbClient;
    }

    /**
     * Setter method for {@link ConnectionFactory} instance
     * 
     * @param _connectionFactory {@link ConnectionFactory}
     */
    public void setConnectionFactory(CIMConnectionFactory connectionFactory) {
        this._connectionFactory = connectionFactory;
    }

    @Override
    public void clearCache() {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        synchronized (cacheLock) {
            SMIS_PROVIDERS_CACHE.clear();
            ACTIVE_SMIS_PROVIDERS_CACHE.clear();
        }
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

}
