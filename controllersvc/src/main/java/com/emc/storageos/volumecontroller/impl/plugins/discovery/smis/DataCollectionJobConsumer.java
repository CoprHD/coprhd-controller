/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.Discovery_Namespaces;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.StorageSystemViewObject;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionJob.JobOrigin;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionJobScheduler.JobIntervals;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;

/**
 * Consumer for Discovery Jobs added to Queue.
 * This acts as core Class, which has multiple responsibilities
 * 1. Scheduled Loading Provider Device from DB every X minutes (Scanning)
 * 1a. Schedule Loading PhsyicalStorageSystems every x minutes. (Discovery)
 * 2. Consume Discovery Jobs
 * 3. Submit the Discovery Jobs to ExecutorService
 * 
 */
public class DataCollectionJobConsumer extends
        DistributedQueueConsumer<DataCollectionJob> implements ApplicationContextAware {
    private static final Logger _logger = LoggerFactory
            .getLogger(DataCollectionJobConsumer.class);

    private Map<String, String> _configInfo;
    private DataCollectionJobUtil _util;
    private DbClient _dbClient;
    private NetworkDeviceController _networkDeviceController;

    private CoordinatorClient _coordinator;
    private CIMConnectionFactory _connectionFactory;
    private DataCollectionJobScheduler _jobScheduler;
    private ControllerLockingService _locker;

    /**
     * 1. Create AccessProfile for Discovery Job
     * 2. Invoke Discovery Runnable, which in turn instructs plugins to get data from DataSources.
     */
    @Override
    public void consumeItem(
            final DataCollectionJob job, final DistributedQueueItemProcessedCallback callback)
            throws Exception {
        try {
            // By the time we get to Discovery/Metering someone could have removed the storage system from Vipr.
            // Check that the job is still "active".
            if (!job.isActiveJob(_dbClient)) {
                return;
            }
            if (job instanceof DataCollectionScanJob) {
                triggerScanning((DataCollectionScanJob) job);
            } else {
                invokeJob(job);
            }

        } catch (InternalException e) {
            _logger.error(job.getType() + " job failed for {}---> ", job.systemString(), e);
            try {
                job.error(_dbClient, e);
            } catch (Exception ex) {
                _logger.error("Failed to record status error for system : {}. Caused by: ", job.systemString(), ex);
            }
        } catch (Exception e) {
            _logger.error(job.getType() + " job failed for {}---> ", job.systemString(), e);
            try {
                ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
                job.error(_dbClient, serviceError);
            } catch (Exception ex) {
                _logger.error("Failed to record status error for system: {}. Caused by: ", job.systemString(), ex);
            }
        } finally {
            try {
                callback.itemProcessed();
            } catch (Exception e) {
                _logger.warn("Queue Item removal failed :"
                        + job.systemString());
            }
        }
    }

    public void invokeJob(final DataCollectionJob job) throws Exception {

        if (job instanceof DataCollectionScanJob) {
            throw new DeviceControllerException("Invoked wrong job type : " + job.getType());
        }
        
        DataCollectionTaskCompleter completer = job.getCompleter();
        // set the next run time based on the time this discovery job is started (not the time it's queued)
        completer.setNextRunTime(_dbClient,
                System.currentTimeMillis() + JobIntervals.get(job.getType()).getInterval() * 1000);
        completer.updateObjectState(_dbClient, DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS);
        
        // get the node that this discovery is being run on so it is displayed in the UI
        String jobType = job.getType();
        String nodeId = _coordinator.getInetAddessLookupMap().getNodeId();
        job.updateTask(_dbClient, "Started " + jobType + " on node " + nodeId);

        /**
         * TODO ISILON or VNXFILE
         * AccessProfile needs to get created, for each device Type.
         * Hence for isilon or vnxFile discovery, add logic in getAccessProfile
         * to set the required parameters for Discovery.
         */
        AccessProfile profile = _util.getAccessProfile(completer.getType(),
                completer.getId(),
                jobType, job.getNamespace());
        profile.setProps(new HashMap<String, String>(_configInfo));
        if (job instanceof DataCollectionArrayAffinityJob) {
            List<URI> hostIds = ((DataCollectionArrayAffinityJob) job).getHostIds();
            if (hostIds != null && !hostIds.isEmpty()) {
                profile.getProps().put(Constants.HOST_IDS, StringUtils.join(hostIds, Constants.ID_DELIMITER));
            }

            List<URI> systemIds = ((DataCollectionArrayAffinityJob) job).getSystemIds();
            if (systemIds != null && !systemIds.isEmpty()) {
                profile.getProps().put(Constants.SYSTEM_IDS, StringUtils.join(systemIds, Constants.ID_DELIMITER));
                Iterator<StorageSystem> storageSystems = _dbClient.queryIterativeObjects(StorageSystem.class, systemIds);
                List<String> systemSerialIds = new ArrayList<String>();
                while (storageSystems.hasNext()) {
                    StorageSystem systemObj = storageSystems.next();
                    systemSerialIds.add(systemObj.getSerialNumber());
                }

                if (!systemSerialIds.isEmpty()) {
                    profile.getProps().put(Constants.SYSTEM_SERIAL_IDS, StringUtils.join(systemSerialIds, Constants.ID_DELIMITER));
                }
            }
        }
        profile.setCimConnectionFactory(_connectionFactory);
        profile.setCurrentSampleTime(System.currentTimeMillis());
        DataCollectionJobInvoker invoker = new DataCollectionJobInvoker(
                profile, _configInfo, _dbClient, _coordinator, _networkDeviceController, _locker, job.getNamespace(), completer);
        invoker.process(applicationContext);
        job.ready(_dbClient);
    }

    /**
     * Once scanning is done for all Providers, we have the list of Discovered Arrays.
     * Check whether the discovered Array is new, using lastRunTime, if yes, start Discovery
     * If refreshAll flag set, then check whether the system's state is inProgress, if yes, skip
     * else, check whether discovery had run recently, if yes skip, else trigger discovery
     * 
     * @param storageSystemsCache
     */
    public void triggerDiscoveryNew(
            Map<String, StorageSystemViewObject> storageSystemsCache, JobOrigin origin) throws Exception {
        Set<String> sysNativeGuidSet = storageSystemsCache.keySet();
        ArrayList<DataCollectionJob> jobs = new ArrayList<DataCollectionJob>();
        for (String sysNativeGuid : sysNativeGuidSet) {
            StorageSystem system = null;
            try {
                List<StorageSystem> systems = CustomQueryUtility.getActiveStorageSystemByNativeGuid(_dbClient, sysNativeGuid);
                if (systems.isEmpty()) {
                    continue;
                }
                system = systems.get(0);
                if (0 == system.getLastDiscoveryRunTime()) {
                    _logger.info("Triggering discovery of new storage system {}",
                            sysNativeGuid);
                    String taskId = UUID.randomUUID().toString();

                    DiscoverTaskCompleter completer = new DiscoverTaskCompleter(system.getClass(), system.getId(), taskId,
                            ControllerServiceImpl.DISCOVERY);
                    jobs.add(new DataCollectionDiscoverJob(completer, origin, Discovery_Namespaces.ALL.toString()));
                }
            } catch (Exception e) {
                _logger.error("Triggering Manual Array Discovery Failed {}:", system, e);
            }
        }
        _jobScheduler.scheduleMultipleJobs(jobs, ControllerServiceImpl.Lock.DISCOVER_COLLECTION_LOCK);

    }

    /**
     * 1. refreshConnections - needs to get called on each Controller, before acquiring lock.
     * 2. Try to acquire lock, if found
     * 3. Acquiring lock is not made as a Blocking Call, hence Controllers will return immediately,
     * if lock not found
     * 3. If lock found, spawn a new thread to do triggerScanning.
     * 4. Release lock immediately.
     */
    private void triggerScanning(DataCollectionScanJob job) throws Exception {
        _logger.info("Started scanning Providers : triggerScanning()");
        
        List<URI> providerList = job.getProviders();
        String providerType = null;
        if (!providerList.isEmpty()) {
            providerType = _dbClient.queryObject(StorageProvider.class, providerList.iterator().next()).getInterfaceType();
        }

        _jobScheduler.refreshProviderConnections(providerType);
        List<URI> allProviderURI = _dbClient.queryByType(StorageProvider.class, true);
        List<StorageProvider> allProvidersAllTypes = _dbClient.queryObject(StorageProvider.class, allProviderURI);
        List<StorageProvider> allProviders = new ArrayList<StorageProvider>();
        // since dbQuery does not return a normal list required by bookkeeping, we need to rebuild it.
        allProviderURI = new ArrayList<URI>();
        for (StorageProvider provider : allProvidersAllTypes) {
            if (providerType == null || providerType.equals(provider.getInterfaceType())) {
                allProviderURI.add(provider.getId());
                allProviders.add(provider);
            }
        }

        Map<String, StorageSystemViewObject> storageSystemsCache = Collections
                .synchronizedMap(new HashMap<String, StorageSystemViewObject>());
        boolean exceptionIntercepted = false;

        /**
         * 
         * Run "Scheduled" Scanner Jobs of all Providers in only one Controller.
         * means our Cache is populated with the latest
         * physicalStorageSystems ID got from this scheduled Scan Job.
         * Compare the list against the ones in DB, and decide the physicalStorageSystem's
         * state REACHABLE
         */
        String lockKey = ControllerServiceImpl.Lock.SCAN_COLLECTION_LOCK.toString();
        if (providerType != null) {
            lockKey += providerType;
        }
        InterProcessLock scanLock = _coordinator.getLock(lockKey);
        if (scanLock.acquire(ControllerServiceImpl.Lock.SCAN_COLLECTION_LOCK.getRecommendedTimeout(), TimeUnit.SECONDS)) {

            _logger.info("Acquired a lock {} to run scanning Job", ControllerServiceImpl.Lock.SCAN_COLLECTION_LOCK.toString()+providerType);
            
            List<URI> cacheProviders = new ArrayList<URI>();
            Map<URI, Exception> cacheErrorProviders = new HashMap<URI, Exception>();
            try {
                boolean scanIsNeeded = false;
                boolean hasProviders = false;

                // First find out if scan is needed. If it needed for a single system , it is needed for all
                for (StorageProvider provider : allProviders) {
                    if (provider.connected() || provider.initializing()) {
                        hasProviders = true;
                        if (_jobScheduler.isProviderScanJobSchedulingNeeded(provider, ControllerServiceImpl.SCANNER, job.isSchedulerJob())) {
                            scanIsNeeded = true;
                            break;
                        }
                    }
                }

                if (!scanIsNeeded) {
                    for (StorageProvider provider : allProviders) {
                        ScanTaskCompleter scanCompleter = job.findProviderTaskCompleter(provider.getId());
                        if (scanCompleter == null) {
                            continue;
                        }
                        if (provider.connected() || provider.initializing()) {
                            scanCompleter.ready(_dbClient);
                        }
                        else {
                            String errMsg = "Failed to establish connection to the storage provider";
                            scanCompleter.error(_dbClient, DeviceControllerErrors.smis.unableToCallStorageProvider(errMsg));
                            provider.setLastScanStatusMessage(errMsg);
                            _dbClient.updateObject(provider);
                        }
                    }
                    if (!hasProviders) {
                        _util.performBookKeeping(storageSystemsCache, allProviderURI);
                    }
                    _logger.info("Scan is not needed");
                }
                else {
                    // If scan is needed for a single system,
                    // it must be performed for all available providers in the database at the same time.
                    // update each provider that is reachable to scan in progress
                    List<StorageProvider> connectedProviders = new ArrayList<StorageProvider>();
                    for (StorageProvider provider : allProviders) {
                        if (provider.connected() || provider.initializing()) {
                            ScanTaskCompleter scanCompleter = job.findProviderTaskCompleter(provider.getId());
                            if (scanCompleter == null) {
                                String taskId = UUID.randomUUID().toString();
                                scanCompleter = new ScanTaskCompleter(StorageProvider.class, provider.getId(), taskId);
                                job.addCompleter(scanCompleter);
                            }
                            scanCompleter.createDefaultOperation(_dbClient);
                            scanCompleter.updateObjectState(_dbClient, DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS);
                            scanCompleter.setNextRunTime(_dbClient, System.currentTimeMillis()
                                    + DataCollectionJobScheduler.JobIntervals.get(ControllerServiceImpl.SCANNER).getInterval() * 1000);
                            provider.setLastScanStatusMessage("");
                            _dbClient.updateObject(provider);
                            connectedProviders.add(provider);
                        } else {
                            if (null != provider.getStorageSystems() &&
                                    !provider.getStorageSystems().isEmpty()) {
                                provider.getStorageSystems().clear();
                            }
                            if (providerList.contains(provider.getId())) {
                                String errMsg = "Failed to establish connection to the storage provider";
                                provider.setLastScanStatusMessage(errMsg);
                                job.findProviderTaskCompleter(provider.getId()).
                                        error(_dbClient, DeviceControllerErrors.smis.unableToCallStorageProvider(errMsg));
                            }
                            _dbClient.updateObject(provider);
                        }
                    }
                    // now scan each connected provider
                    for (StorageProvider provider : connectedProviders) {
                        try {
                            _logger.info("provider.getInterfaceType():{}", provider.getInterfaceType());
                            ScanTaskCompleter scanCompleter = job.findProviderTaskCompleter(provider.getId());
                            performScan(provider.getId(), scanCompleter, storageSystemsCache);
                            cacheProviders.add(provider.getId());
                        } catch (Exception ex) {
                            _logger.error("Scan failed for {}--->", provider.getId(), ex);
                            cacheErrorProviders.put(provider.getId(), ex);
                        }
                    }
                    // Perform BooKKeeping
                    // TODO: we need to access the status of job completer.
                    // for now we assume that this operation can not fail.
                    _util.performBookKeeping(storageSystemsCache, allProviderURI);

                }
            } catch (final Exception ex) {
                _logger.error("Scan failed for {} ", ex.getMessage());
                exceptionIntercepted = true;
                for (URI provider : cacheProviders) {
                    job.findProviderTaskCompleter(provider).error(_dbClient, DeviceControllerErrors.dataCollectionErrors.scanFailed(ex.getLocalizedMessage(), ex));
                    _logger.error("Scan failed for {}--->", provider, ex);
                }
                throw ex;
            } finally {
                if (!exceptionIntercepted) {
                    for (URI provider : cacheProviders) {
                        job.findProviderTaskCompleter(provider).ready(_dbClient);
                        _logger.info("Scan complete successfully for " + provider);
                    }
                }
                for (Entry<URI, Exception> entry : cacheErrorProviders.entrySet()) {
                    URI provider = entry.getKey();
                    Exception ex = entry.getValue();
                    job.findProviderTaskCompleter(provider).error(_dbClient, DeviceControllerErrors.dataCollectionErrors.scanFailed(ex.getLocalizedMessage(), ex));
                }
                
                scanLock.release();
                _logger.info("Released a lock {} to run scanning Job", lockKey);
                
                try {
                    if (!exceptionIntercepted /* && job.isSchedulerJob() */) {
                        // Manually trigger discoveries, if any new Arrays detected
                        triggerDiscoveryNew(storageSystemsCache, 
                                (job.isSchedulerJob() ? DataCollectionJob.JobOrigin.SCHEDULER : DataCollectionJob.JobOrigin.USER_API));
                    }
                } catch (Exception ex) {
                    _logger.error("Exception occurred while triggering discovery of new systems", ex);
                }
            }
        }
        else {
            job.setTaskError(_dbClient, DeviceControllerErrors.dataCollectionErrors.scanLockFailed());
            _logger.error("Not able to Acquire Scanning {} lock-->{}", lockKey, Thread
                    .currentThread().getId());
        }
    }

    public void performScan(URI provider,
            ScanTaskCompleter scanCompleter,
            Map<String, StorageSystemViewObject> storageCache) throws DatabaseException, BaseCollectionException, DeviceControllerException {
        AccessProfile profile = _util.getAccessProfile(StorageProvider.class, provider, ControllerServiceImpl.SCANNER, null);
        profile.setCache(storageCache);
        profile.setCimConnectionFactory(_connectionFactory);
        profile.setProps(_configInfo);
        DataCollectionJobInvoker invoker = new DataCollectionJobInvoker(
                profile, _configInfo, _dbClient, _coordinator, null, _locker, null, scanCompleter);
        invoker.process(applicationContext);
    }

    public void start() throws Exception {
    }

    public void stop() throws Exception {
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    /**
     * Set CoordinatorClient
     * 
     * @param coordinator
     */
    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    public void setUtil(DataCollectionJobUtil util) {
        _util = util;
    }

    public void setConnectionFactory(CIMConnectionFactory cimConnectionFactory) {
        _connectionFactory = cimConnectionFactory;
    }

    public void setJobScheduler(DataCollectionJobScheduler jobScheduler) {
        _jobScheduler = jobScheduler;
    }

    public void setConfigInfo(Map<String, String> configInfo) {
        _configInfo = configInfo;
    }

    /**
     * Set the controller locking service.
     * 
     * @param locker An instance of ControllerLockingService
     */
    public void setLocker(ControllerLockingService locker) {
        _locker = locker;
    }

    private ApplicationContext applicationContext = null;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public NetworkDeviceController getNetworkDeviceController() {
        return _networkDeviceController;
    }

    public void setNetworkDeviceController(
            NetworkDeviceController networkDeviceController) {
        this._networkDeviceController = networkDeviceController;
    }
}
