/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.Discovery_Namespaces;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.plugins.StorageSystemViewObject;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionJob.JobOrigin;

/*
 * Based on DataCollectionJobConsumer
 * Modified for standalone testing
 */
public class TestDataCollectionJobConsumer extends DataCollectionJobConsumer {
    private static final Logger _logger = LoggerFactory
            .getLogger(TestDataCollectionJobConsumer.class);

    private DbClient _dbClient = null;
    private DataCollectionJobUtil _util = null;

    @Override
    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
        super.setDbClient(dbClient);
    }

    @Override
    public void setUtil(DataCollectionJobUtil util) {
        _util = util;
        super.setUtil(util);
    }

    @Override
    public void consumeItem(final DataCollectionJob job,
            final DistributedQueueItemProcessedCallback callback)
            throws Exception {
        try {
            if (job instanceof DataCollectionScanJob) {
                triggerScanning((DataCollectionScanJob) job);
            } else {
                invokeJob(job);
            }
        } catch (ControllerException e) {
            _logger.error(job.getType() + " job failed for {}---> ",
                    job.systemString(), e);
        } catch (Exception e) {
            _logger.error(job.getType() + " job failed for {}---> ",
                    job.systemString(), e);
        }
    }

    private void triggerScanning(DataCollectionScanJob job) throws Exception {
        _logger.info("Started scanning SMIS Providers : triggerScanning()");
        List<URI> allProviderURI = _dbClient.queryByType(StorageProvider.class,
                true);
        List<StorageProvider> allProviders = _dbClient.queryObject(
                StorageProvider.class, allProviderURI);
        Map<String, StorageSystemViewObject> storageSystemsCache = Collections
                .synchronizedMap(new HashMap<String, StorageSystemViewObject>());
        boolean exceptionIntercepted = false;
        try {
            List<URI> cacheProviders = new ArrayList<URI>();
            // since dbQuery does not return a normal list required by
            // bookkeeping, we need to rebuild it.
            allProviderURI = new ArrayList<URI>();
            // If scan is needed for a single system,
            // it must be performed for all available providers in the database
            // at the same time.
            for (StorageProvider provider : allProviders) {
                allProviderURI.add(provider.getId());
                ScanTaskCompleter scanCompleter = job
                        .findProviderTaskCompleter(provider.getId());
                if (scanCompleter == null) {
                    String taskId = UUID.randomUUID().toString();
                    scanCompleter = new ScanTaskCompleter(
                            StorageProvider.class, provider.getId(), taskId);
                    job.addCompleter(scanCompleter);
                }

                try {
                    provider.setLastScanStatusMessage("");
                    _dbClient.persistObject(provider);
                    _logger.info("provider.getInterfaceType():{}",
                            provider.getInterfaceType());
                    performScan(provider.getId(), scanCompleter,
                            storageSystemsCache);
                    cacheProviders.add(provider.getId());
                } catch (Exception ex) {
                    _logger.error("Scan failed for {}--->", provider.getId(),
                            ex);
                }

                _dbClient.persistObject(provider);
            }
            // Perform BooKKeeping
            // TODO: we need to access the status of job completer.
            // for now we assume that this operation can not fail.
            _util.performBookKeeping(storageSystemsCache, allProviderURI);

            for (URI provider : cacheProviders) {
                job.findProviderTaskCompleter(provider).ready(_dbClient);
                _logger.info("Scan complete successfully for " + provider);
            }
        } catch (final Exception ex) {
            _logger.error("Scan failed for {} ", ex.getMessage());
            job.error(_dbClient,
                    DeviceControllerErrors.dataCollectionErrors.scanFailed(ex.getLocalizedMessage(), ex));
            exceptionIntercepted = true;
            throw ex;
        } finally {
            try {
                if (!exceptionIntercepted && job.isSchedulerJob()) {
                    // Manually trigger discoveries, if any new Arrays detected
                    triggerDiscoveryNew(storageSystemsCache, DataCollectionJob.JobOrigin.SCHEDULER);
                }
            } catch (Exception ex) {
                _logger.error(ex.getMessage(), ex);
            }
        }
    }

    @Override
    public void triggerDiscoveryNew(
            Map<String, StorageSystemViewObject> storageSystemsCache, JobOrigin origin)
            throws Exception {
        Set<String> sysNativeGuidSet = storageSystemsCache.keySet();
        for (String sysNativeGuid : sysNativeGuidSet) {
            StorageSystem system = null;
            try {
                List<StorageSystem> systems = CustomQueryUtility
                        .getActiveStorageSystemByNativeGuid(_dbClient,
                                sysNativeGuid);
                if (systems.isEmpty()) {
                    continue;
                }
                system = systems.get(0);
                _logger.info("Triggering discovery of new storage system {}",
                        sysNativeGuid);
                String taskId = UUID.randomUUID().toString();

                DiscoverTaskCompleter completer = new DiscoverTaskCompleter(
                        system.getClass(), system.getId(), taskId, ControllerServiceImpl.DISCOVERY);

                invokeJob(new DataCollectionDiscoverJob(completer,
                        Discovery_Namespaces.ALL.toString()));
            } catch (Exception e) {
                _logger.error("Triggering Manual Array Discovery Failed {}:",
                        system, e);
            }
        }
    }
}
