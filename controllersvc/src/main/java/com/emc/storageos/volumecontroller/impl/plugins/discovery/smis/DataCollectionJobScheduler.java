/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.LeaderSelectorListenerForPeriodicTask;
import com.emc.storageos.coordinator.client.service.impl.LeaderSelectorListenerImpl;
import com.emc.storageos.datadomain.restapi.DataDomainClientFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.Discovery_Namespaces;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.hds.api.HDSApiFactory;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ceph.CephUtils;
import com.emc.storageos.volumecontroller.impl.cinder.CinderUtils;
import com.emc.storageos.volumecontroller.impl.datadomain.DataDomainUtils;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.PortMetricsProcessor;
import com.emc.storageos.volumecontroller.impl.scaleio.ScaleIOStorageDevice;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.xtremio.prov.utils.XtremIOProvUtils;
import com.emc.storageos.vplexcontroller.VPlexDeviceController;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;

/**
 * Consumer for Discovery Jobs added to Queue.
 * This acts as core Class, which has multiple responsibilities
 * 1. Scheduled Loading Provider Device from DB every X minutes (Scanning)
 * 1a. Schedule Loading PhsyicalStorageSystems every x minutes. (Discovery)
 * 2. Consume Discovery Jobs
 * 3. Submit the Discovery Jobs to ExecutorService
 * 
 */
public class DataCollectionJobScheduler {
    private static final Logger _logger = LoggerFactory
            .getLogger(DataCollectionJobScheduler.class);
    private ScheduledExecutorService _dataCollectionExecutorService = null;
    private static final String ENABLE_METERING = "enable-metering";
    private static final String ENABLE_AUTODISCOVER = "enable-autodiscovery";
    private static final String ENABLE_AUTOSCAN = "enable-autoscan";
    private static final String TOLERANCE = "time-tolerance";
    private static final String PROP_HEADER_CONTROLLER = "controller_";

    private static final int initialScanDelay = 30;
    private static final int initialDiscoveryDelay = 90;
    private static final int initialMeteringDelay = 60;
    private static final int initialConnectionRefreshDelay = 10;

    private Map<String, String> _configInfo;
    private DbClient _dbClient;
    private CoordinatorClient _coordinator;
    private CIMConnectionFactory _connectionFactory;
    private final String leaderSelectorPath = "discoveryleader";
    private final String leaderSelectorComputePortMetricsPath = "computeportmetricsleader";
    private LeaderSelector discoverySchedulingSelector;
    private HDSApiFactory hdsApiFactory;
    private DataDomainClientFactory ddClientFactory;
    private XtremIOClientFactory xioClientFactory;
    private PortMetricsProcessor _portMetricsProcessor;
    private LeaderSelector computePortMetricsSelector;

    static enum JobIntervals {

        SCAN_INTERVALS("scan-interval", "scan-refresh-interval", initialScanDelay),
        DISCOVER_INTERVALS("discovery-interval", "discovery-refresh-interval", initialDiscoveryDelay),
        CS_DISCOVER_INTERVALS("cs-discovery-interval", "cs-discovery-refresh-interval", initialDiscoveryDelay),
        NS_DISCOVER_INTERVALS("ns-discovery-interval", "ns-discovery-refresh-interval", initialDiscoveryDelay),
        COMPUTE_DISCOVER_INTERVALS("compute-discovery-interval", "compute-discovery-refresh-interval", initialDiscoveryDelay),
        METERING_INTERVALS("metering-interval", "metering-refresh-interval", initialMeteringDelay);

        private final String _interval;
        private volatile long _intervalValue;
        private final String _refreshInterval;
        private volatile long _refreshIntervalValue;
        private final long _initialDelay;

        static private long _maximumIdleInterval;

        JobIntervals(String interval, String refresh, long delay) {
            _interval = interval;
            _refreshInterval = refresh;
            _initialDelay = delay;
        }

        void initialize(Map<String, String> configInfo) {
            _intervalValue = Long.parseLong(configInfo.get(_interval));
            _refreshIntervalValue = Long.parseLong(configInfo.get(_refreshInterval));
            _maximumIdleInterval = Long.parseLong(configInfo.get("maximum-idle-timeout"));
        }

        public long getInterval() {
            return _intervalValue;
        }

        public long getRefreshInterval() {
            return _refreshIntervalValue;
        }

        public long getInitialDelay() {
            return _initialDelay;
        }

        public static JobIntervals get(String jobType) {
            if (ControllerServiceImpl.SCANNER.equalsIgnoreCase(jobType)) {
                return SCAN_INTERVALS;
            }
            if (ControllerServiceImpl.DISCOVERY.equalsIgnoreCase(jobType)) {
                return DISCOVER_INTERVALS;
            }
            if (ControllerServiceImpl.NS_DISCOVERY.equalsIgnoreCase(jobType)) {
                return NS_DISCOVER_INTERVALS;
            }
            if (ControllerServiceImpl.CS_DISCOVERY.equalsIgnoreCase(jobType)) {
                return CS_DISCOVER_INTERVALS;
            }
            if (ControllerServiceImpl.METERING.equalsIgnoreCase(jobType)) {
                return METERING_INTERVALS;
            }
            if (ControllerServiceImpl.COMPUTE_DISCOVERY.equalsIgnoreCase(jobType)) {
                return COMPUTE_DISCOVER_INTERVALS;
            } else {
                return null;
            }
        }

        public static long getMaxIdleInterval() {
            return _maximumIdleInterval;
        }
    }

    public void start() throws Exception {
        _dataCollectionExecutorService = Executors.newScheduledThreadPool(1);

        for (JobIntervals intervals : JobIntervals.values()) {
            // Override intervals and refresh intervals with system properties, if set.
            // Requires these system props start with "controller_" and uses underscores instead of hyphens.
            String prop = _coordinator.getPropertyInfo().getProperty(PROP_HEADER_CONTROLLER + intervals._interval.replace('-', '_'));
            if (prop != null) {
                _configInfo.put(intervals._interval, prop);
            }
            prop = _coordinator.getPropertyInfo().getProperty(PROP_HEADER_CONTROLLER + intervals._refreshInterval.replace('-', '_'));
            if (prop != null) {
                _configInfo.put(intervals._refreshInterval, prop);
            }

            intervals.initialize(_configInfo);
        }

        boolean enableAutoScan = Boolean.parseBoolean(_configInfo.get(ENABLE_AUTOSCAN));
        boolean enableAutoDiscovery = Boolean.parseBoolean(_configInfo.get(ENABLE_AUTODISCOVER));
        boolean enableAutoMetering = Boolean.parseBoolean(_configInfo.get(ENABLE_METERING));

        LeaderSelectorListenerForPeriodicTask schedulingProcessor = new LeaderSelectorListenerForPeriodicTask(
                _dataCollectionExecutorService);

        if (enableAutoScan) {
            JobIntervals intervals = JobIntervals.get(ControllerServiceImpl.SCANNER);
            schedulingProcessor.addScheduledTask(new DiscoveryScheduler(ControllerServiceImpl.SCANNER),
                    intervals.getInitialDelay(),
                    intervals.getInterval());
        }
        if (enableAutoDiscovery) {
            JobIntervals intervals = JobIntervals.get(ControllerServiceImpl.DISCOVERY);
            schedulingProcessor.addScheduledTask(new DiscoveryScheduler(ControllerServiceImpl.DISCOVERY),
                    intervals.getInitialDelay(),
                    intervals.getInterval());
            intervals = JobIntervals.get(ControllerServiceImpl.NS_DISCOVERY);
            schedulingProcessor.addScheduledTask(new DiscoveryScheduler(ControllerServiceImpl.NS_DISCOVERY),
                    intervals.getInitialDelay(),
                    intervals.getInterval());

            intervals = JobIntervals.get(ControllerServiceImpl.COMPUTE_DISCOVERY);
            schedulingProcessor.addScheduledTask(new DiscoveryScheduler(ControllerServiceImpl.COMPUTE_DISCOVERY),
                    intervals.getInitialDelay(),
                    intervals.getInterval());

            intervals = JobIntervals.get(ControllerServiceImpl.CS_DISCOVERY);
            schedulingProcessor.addScheduledTask(new DiscoveryScheduler(ControllerServiceImpl.CS_DISCOVERY),
                    intervals.getInitialDelay(),
                    intervals.getInterval());
        }
        if (enableAutoMetering) {
            JobIntervals intervals = JobIntervals.get(ControllerServiceImpl.METERING);
            schedulingProcessor.addScheduledTask(new DiscoveryScheduler(ControllerServiceImpl.METERING),
                    intervals.getInitialDelay(),
                    intervals.getInterval());
        }
        else {
            _logger.info("Metering is disabled.");
        }

        discoverySchedulingSelector = _coordinator.getLeaderSelector(leaderSelectorPath,
                schedulingProcessor);
        discoverySchedulingSelector.autoRequeue();
        discoverySchedulingSelector.start();

        _dataCollectionExecutorService.scheduleAtFixedRate(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            refreshProviderConnections();
                        } catch (Exception e) {
                            _logger.info("Failed to refresh connections: {}", e.getMessage(), e);
                        }
                    }
                }, initialConnectionRefreshDelay, JobIntervals.SCAN_INTERVALS.getInterval(), TimeUnit.SECONDS);

        // recompute storage ports's metrics for all storage system
        // Since traverse through all storage ports in all storage systems may take a while, it best to perform the
        // task in a thread. We definitely do not want all nodes in cluster to do the same task, select a leader to
        // do it there.
        computePortMetricsSelector = _coordinator.getLeaderSelector(leaderSelectorComputePortMetricsPath, new LeaderSelectorListenerImpl() {

            @Override
            protected void stopLeadership() {
            }

            @Override
            protected void startLeadership() throws Exception {
                _dataCollectionExecutorService.schedule(new Runnable() {

                    @Override
                    public void run() {
                        _portMetricsProcessor.computeStoragePortUsage();
                    }
                }, 1, TimeUnit.MILLISECONDS);
            }
        });
        computePortMetricsSelector.autoRequeue();
        computePortMetricsSelector.start();

    }

    private class DiscoveryScheduler implements Runnable {
        String jobType;

        public DiscoveryScheduler(String jobType) {
            this.jobType = jobType;
        }

        @Override
        public void run() {
            try {
                if (ControllerServiceImpl.SCANNER.equalsIgnoreCase(jobType)) {
                    scheduleScannerJobs();
                } else {
                    loadSystemfromDB(jobType);
                }
            } catch (Exception e) {
                _logger.error(String.format("Exception caught when trying to run discovery job %s", jobType), e);
            }
        }
    }

    /**
     * Core method, responsible for loading StorageProviders from DB and do scanning.
     * 
     * @throws Exception
     */
    private void scheduleScannerJobs() throws Exception {
        _logger.info("Started Loading Storage Providers from DB");
        List<URI> providerUris = _dbClient.queryByType(StorageProvider.class, true);
        List<StorageProvider> providers = _dbClient.queryObject(StorageProvider.class, providerUris);
        DataCollectionScanJob scanJob = new DataCollectionScanJob(DataCollectionJob.JobOrigin.SCHEDULER);

        for (StorageProvider provider : providers) {
            URI providerURI = provider.getId();
            String taskId = UUID.randomUUID().toString();
            scanJob.addCompleter(new ScanTaskCompleter(StorageProvider.class, providerURI, taskId));
        }

        long lastScanTime = 0;
        boolean inProgress = true;

        List<URI> provUris = scanJob.getProviders();
        if (provUris != null && !provUris.isEmpty()) {
            ControllerServiceImpl.Lock lock = ControllerServiceImpl.Lock.getLock(ControllerServiceImpl.SCANNER);
            if (lock.acquire(lock.getRecommendedTimeout())) {
                try {
                    _logger.info("Acquired a lock {} to schedule Jobs", lock.toString());
                    // Find the last scan time from the provider whose scan status is not in progress or scheduled
                    for (StorageProvider provider : providers) {
                        if (!isInProgress(provider)) {
                            lastScanTime = provider.getLastScanTime();
                            inProgress = false;
                            break;
                        }
                    }
                    if (isDataCollectionScanJobSchedulingNeeded(lastScanTime, inProgress)) {
                        _logger.info("Added Scan job to the Distributed Queue");
                        ControllerServiceImpl.enqueueDataCollectionJob(scanJob);
                    }
                } finally {
                    try {
                        lock.release();
                    } catch (Exception e) {
                        _logger.error("Failed to release  Lock {} -->{}", lock.toString(), e.getMessage());
                    }
                }
            } else {
                _logger.debug("Not able to Acquire lock {}-->{}", lock.toString(), Thread.currentThread().getId());
                throw new DeviceControllerException("Failed to acquire lock : " + lock.toString());
            }
        }
    }

    private void addToList(List<URI> newList, Iterator<URI> iter) {
        while (iter.hasNext()) {
            newList.add(iter.next());
        }
    }

    /**
     * Load Physical Systems from DB, and add to Discovery Job Queue
     * 
     * @throws Exception
     */
    private void loadSystemfromDB(String jobType) throws Exception {
        _logger.info("Started Loading Systems from DB for " + jobType + " jobs");
        ArrayList<DataCollectionJob> jobs = new ArrayList<DataCollectionJob>();
        List<URI> allSystemsURIs = new ArrayList<URI>();
        if (jobType.equalsIgnoreCase(ControllerServiceImpl.NS_DISCOVERY)) {
            addToList(allSystemsURIs, _dbClient.queryByType(NetworkSystem.class, true).iterator());
        } else if (jobType.equalsIgnoreCase(ControllerServiceImpl.CS_DISCOVERY)) {
            addToList(allSystemsURIs, _dbClient.queryByType(Host.class, true).iterator());
            addToList(allSystemsURIs, _dbClient.queryByType(Vcenter.class, true).iterator());
        } else if (jobType.equalsIgnoreCase(ControllerServiceImpl.COMPUTE_DISCOVERY)) {
            addToList(allSystemsURIs, _dbClient.queryByType(ComputeSystem.class, true).iterator());
        }
        else {
            addToList(allSystemsURIs, _dbClient.queryByType(StorageSystem.class, true).iterator());
            addToList(allSystemsURIs, _dbClient.queryByType(ProtectionSystem.class, true).iterator());
        }

        if (!allSystemsURIs.isEmpty()) {
            Iterator<URI> systemURIsItr = allSystemsURIs.iterator();
            while (systemURIsItr.hasNext()) {
                URI systemURI = systemURIsItr.next();
                String taskId = UUID.randomUUID().toString();
                DataCollectionJob job = null;
                if (URIUtil.isType(systemURI, StorageSystem.class)) {
                    StorageSystem systemObj = _dbClient.queryObject(StorageSystem.class, systemURI);
                    if (systemObj == null) {
                        _logger.warn(String.format("StorageSystem %s is no longer in the DB. It could have been deleted or decommissioned",
                                systemURI));
                        continue;
                    }
                    // check devices managed by SMIS/hicommand/vplex device mgr has ActiveProviderURI or not.
                    if (systemObj.storageSystemHasProvider()) {
                        if (systemObj.getActiveProviderURI() == null
                                || NullColumnValueGetter.getNullURI().equals(systemObj.getActiveProviderURI())) {
                            _logger.info("Skipping {} Job : StorageSystem {} does not have an active provider",
                                    jobType, systemURI);
                            continue;
                        }
                        StorageProvider provider = _dbClient.queryObject(StorageProvider.class,
                                systemObj.getActiveProviderURI());
                        if (provider == null || provider.getInactive()) {
                            _logger.info("Skipping {} Job : StorageSystem {} does not have a valid active provider",
                                    jobType, systemURI);
                            continue;
                        }
                    }
                    // For Metering, check SerialNumber has populated or not.
                    if (ControllerServiceImpl.METERING.equalsIgnoreCase(jobType)) {
                        if (null == systemObj.getSerialNumber()) {
                            _logger.info("Skipping {} Job : StorageSystem {} discovery failed or hasn't run.",
                                    jobType, systemURI);
                            continue;
                        } else if (CompatibilityStatus.INCOMPATIBLE.name().equalsIgnoreCase(systemObj.getCompatibilityStatus())) {
                            _logger.info("Skipping {} Job : StorageSystem {} has incompatible version",
                                    jobType, systemURI);
                            continue;
                        }
                    }
                    job = getDataCollectionJobByType(StorageSystem.class, jobType, taskId, systemURI);
                } else if (URIUtil.isType(systemURI, NetworkSystem.class)) {
                    job = getDataCollectionJobByType(NetworkSystem.class, jobType, taskId, systemURI);
                } else if (URIUtil.isType(systemURI, ComputeSystem.class)) {
                    job = getDataCollectionJobByType(ComputeSystem.class, jobType, taskId, systemURI);
                } else if (URIUtil.isType(systemURI, Host.class)) {
                    Host host = _dbClient.queryObject(Host.class, systemURI);
                    // Add host
                    if ((host.getDiscoverable() == null || host.getDiscoverable())) {
                        job = getDataCollectionJobByType(Host.class, jobType, taskId, systemURI);
                    }
                } else if (URIUtil.isType(systemURI, Vcenter.class)) {
                    job = getDataCollectionJobByType(Vcenter.class, jobType, taskId, systemURI);
                } else if (URIUtil.isType(systemURI, ProtectionSystem.class)) {
                    // Do not queue any metering jobs for ProtectionSystems.
                    // Protection System metrics are not used for "metering" per vpool/project/tenant
                    if (!jobType.equals(ControllerServiceImpl.METERING)) {
                        job = getDataCollectionJobByType(ProtectionSystem.class, jobType, taskId, systemURI);
                    }
                }
                if (null != job) {
                    jobs.add(job);
                }
            }
            scheduleMultipleJobs(jobs, ControllerServiceImpl.Lock.getLock(jobType));
        } else {
            _logger.info("No systems found in db to schedule jobs.");
        }
    }

    /**
     * Return the job based on its type.
     * 
     * @param systemClass : System Object to create TaskCompleter.
     * @param jobType : JobType tells which type of job to create.
     * @param taskId : TaskID to set in TaskCompleter.
     * @param systemURI : systemURI to set in TaskCompleter.
     * @return
     */
    private DataCollectionJob getDataCollectionJobByType(Class<? extends DiscoveredSystemObject> systemClass,
            String jobType, String taskId, URI systemURI) {
        DataCollectionJob job = null;
        if (ControllerServiceImpl.METERING.equalsIgnoreCase(jobType)) {
            MeteringTaskCompleter completer = new MeteringTaskCompleter(systemClass, systemURI,
                    taskId);
            job = new DataCollectionMeteringJob(completer, DataCollectionJob.JobOrigin.SCHEDULER);
        } else if (ControllerServiceImpl.isDiscoveryJobTypeSupported(jobType)) {
            DiscoverTaskCompleter completer = new DiscoverTaskCompleter(systemClass, systemURI, taskId, jobType);
            job = new DataCollectionDiscoverJob(completer, DataCollectionJob.JobOrigin.SCHEDULER, Discovery_Namespaces.ALL.toString());
        }

        return job;
    }

    public void scheduleMultipleJobs(List<DataCollectionJob> jobs, ControllerServiceImpl.Lock lock) throws Exception {
        if (lock.acquire(lock.getRecommendedTimeout())) {
            try {
                _logger.info("Acquired a lock {} to schedule Jobs", lock.toString());
                enqueueJobs(jobs);
            } finally {
                try {
                    lock.release();
                } catch (Exception e) {
                    _logger.error("Failed to release  Lock {} -->{}", lock.toString(), e.getMessage());
                }
            }
        } else {
            _logger.debug("Not able to Acquire lock {}-->{}", lock.toString(), Thread.currentThread().getId());
            throw new DeviceControllerException("Failed to acquire lock : " + lock.toString());
        }
    }

    private void enqueueJobs(List<DataCollectionJob> jobs) {
        for (DataCollectionJob job : jobs) {
            try {
                DataCollectionTaskCompleter completer = job.getCompleter();
                DiscoveredSystemObject system = (DiscoveredSystemObject)
                        _dbClient.queryObject(completer.getType(), completer.getId());
                if (isDataCollectionJobSchedulingNeeded(system,
                        job.getType(), job.isSchedulerJob(), job.getNamespace())) {
                    job.schedule(_dbClient);
                    system.setLastDiscoveryStatusMessage("");
                    _dbClient.persistObject(system);
                    completer.setNextRunTime(_dbClient,
                            System.currentTimeMillis() + JobIntervals.get(job.getType()).getInterval() * 1000);
                    ControllerServiceImpl.enqueueDataCollectionJob(job);
                }
                else {
                    _logger.info("Skipping {} Job for {}", job.getType(), completer.getId());
                    if (!job.isSchedulerJob()) {
                        job.setTaskReady(_dbClient,
                                "The discovery for this system is currently running or was run quite recently. Resubmit this request at a later time, if needed.");
                    }
                }
            } catch (Exception e) {
                _logger.error("Failed to enqueue {} Job  {}", job.getType(), e.getMessage());
                if (!job.isSchedulerJob()) {
                    try {
                        job.setTaskError(_dbClient,
                                DeviceControllerErrors.dataCollectionErrors.failedToEnqueue(job.getType(), e));
                    } catch (Exception ex) {
                        _logger.warn("Exception occurred while updating task status", ex);
                    }
                }
            }

        }
    }

    private <T extends DiscoveredSystemObject> boolean isInProgress(
            T storageSystem, String type) {
        // if inprogress,
        String progressStatus = getStatus(storageSystem, type);
        return DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS.toString()
                .equalsIgnoreCase(progressStatus) ||
                DiscoveredDataObject.DataCollectionJobStatus.SCHEDULED.toString()
                        .equalsIgnoreCase(progressStatus);
    }

    private boolean isInProgress(StorageProvider provider) {
        // if inprogress,
        return DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS.toString()
                .equalsIgnoreCase(provider.getScanStatus()) ||
                DiscoveredDataObject.DataCollectionJobStatus.SCHEDULED.toString()
                        .equalsIgnoreCase(provider.getScanStatus());
    }

    private <T extends DiscoveredSystemObject> boolean isError(
            T storageSystem, String type) {
        String progressStatus = getStatus(storageSystem, type);
        return DiscoveredDataObject.DataCollectionJobStatus.ERROR.toString()
                .equalsIgnoreCase(progressStatus);
    }

    private boolean isError(StorageProvider provider) {
        return DiscoveredDataObject.DataCollectionJobStatus.ERROR.toString()
                .equalsIgnoreCase(provider.getScanStatus());
    }

    /**
     * If the job is in progress, don't schedule
     * the job.
     * If not in progress, then schedule if refresh interval is satisfied.
     * 
     * @param lastScanTime
     * @return
     */
    private <T extends DiscoveredSystemObject> boolean isDataCollectionScanJobSchedulingNeeded(long lastScanTime, boolean inProgress) {
        long systemTime = System.currentTimeMillis();
        long refreshInterval = getRefreshInterval(ControllerServiceImpl.SCANNER);

        // Job is in progress
        if (inProgress) {
            return false;
        }
        // If not in progress, check that refresh interval is satisfied.
        if (lastScanTime > 0 && (systemTime - lastScanTime < refreshInterval * 1000)) {
            _logger.info("Skipping scanner job; attempt to schedule faster than refresh interval allows");
            return false;
        }

        return true;
    }

    /**
     * 
     * @param <T>
     * @param system
     * @param scheduler indicates if the job is initiated automatically by scheduler or if it is
     *            requested by a user.
     * @return
     */
    private <T extends DiscoveredSystemObject> boolean isDataCollectionJobSchedulingNeeded(
            T system, String type, boolean scheduler, String namespace) {

        // CTRL-8227 if an unmanaged volume discovery is requested by the user,
        // just run it regardless of last discovery time
        // COP-20052 if an unmanaged CG discovery is requested, just run it
        if (!scheduler &&
                (Discovery_Namespaces.UNMANAGED_VOLUMES.name().equalsIgnoreCase(namespace) ||
                        Discovery_Namespaces.BLOCK_SNAPSHOTS.name().equalsIgnoreCase(namespace) ||
                        Discovery_Namespaces.UNMANAGED_FILESYSTEMS.name().equalsIgnoreCase(namespace) ||
                Discovery_Namespaces.UNMANAGED_CGS.name().equalsIgnoreCase(namespace))) {
            _logger.info(namespace + " discovery has been requested by the user, scheduling now...");
            return true;
        }

        if (ControllerServiceImpl.METERING.equalsIgnoreCase(type) &&
                !DiscoveredDataObject.RegistrationStatus.REGISTERED.toString()
                        .equalsIgnoreCase(system.getRegistrationStatus())) {
            return false;
        }

        // Scan triggered the discovery of this new System found, and discovery was in progress
        // in the mean time, UI triggered the discovery again, the last Run time will be 0
        // as we depend on the last run time to calculate next run time, the value will be
        // always 3600 seconds in this case, which is lower than the maximum idle interval which is 4200 sec.
        // hence a new Job will again get rescheduled.
        // This fix, calculates next time from last Run time , only if its not 0.

        long lastTime = getLastRunTime(system, type);
        long nextTime = getNextRunTime(system, type);

        if (lastTime > 0) {
            nextTime = lastTime + JobIntervals.get(type).getInterval() * 1000;
        }

        if (ControllerServiceImpl.DISCOVERY.equalsIgnoreCase(type) && system instanceof NetworkSystem) {
            type = ControllerServiceImpl.NS_DISCOVERY;
        }

        if (ControllerServiceImpl.DISCOVERY.equalsIgnoreCase(type) && system instanceof ComputeSystem) {
            type = ControllerServiceImpl.COMPUTE_DISCOVERY;
        }

        if (ControllerServiceImpl.DISCOVERY.equalsIgnoreCase(type) &&
                (system instanceof Host || system instanceof Vcenter)) {
            type = ControllerServiceImpl.CS_DISCOVERY;
        }

        return isJobSchedulingNeeded(system.getId(), type, isInProgress(system, type), isError(system, type), scheduler, lastTime, nextTime);
    }

    /**
     * 
     * @param provider
     * @param scheduler indicates if the job is initiated automatically by scheduler or if it is
     *            requested by a user.
     * @return
     */
    boolean isProviderScanJobSchedulingNeeded(StorageProvider provider, String type, boolean scheduler) {

        long nextTime = provider.getNextScanTime();
        long lastTime = provider.getLastScanTime();

        return isJobSchedulingNeeded(provider.getId(), type, isInProgress(provider), isError(provider), scheduler, lastTime, nextTime);
    }

    /**
     * If current time - lastRunTime is > refreshInterval, then schedule
     * 
     * @param inProgress indicates if the job is in progress or not.
     * @param scheduler indicates if the job is initiated automatically by scheduler or if it is
     *            requested by a user.
     */
    private boolean isJobSchedulingNeeded(URI id, String type, boolean inProgress,
            boolean isError, boolean scheduler, long lastTime, long nextTime) {
        long systemTime = System.currentTimeMillis();
        long tolerance = Long.parseLong(_configInfo.get(TOLERANCE)) * 1000;
        _logger.info("Next Run Time {} , Last Run Time {}", nextTime, lastTime);
        long refreshInterval = getRefreshInterval(type);
        if (!inProgress) {
            // First for job, that is scheduled, is compared against the "next time"
            // it expected to be started by the scheduler thread
            if (scheduler) {
                if (systemTime < nextTime - tolerance) {
                    _logger.info("Skipping Job {} ; attempt to schedule it before the next run time  :{}",
                            id + "of type " + type, new Date(nextTime));
                    _logger.info("Current system time {}; tolerance time allowed {}.", new Date(systemTime), tolerance);
                    return false;
                }
            }
            // CTRL-10655 - if manual discovery is requested and the discovery status is error, then
            // schedule the job
            if (!scheduler && isError && lastTime > 0) {
                _logger.info("User triggered {} Job for {} whose discovery status is error. Reschedule the job", type, id);
                return true;
            }
            // For all jobs, check that refresh interval is satisfied.
            if (systemTime - lastTime < refreshInterval * 1000) {
                _logger.info("Skipping Job {} of type {}; attempt to schedule faster than refresh interval allows",
                        id, type);
                return false;
            }
        }
        // If in progress. We still needs to check that this status is not a
        // left-over from unsuccessful thread (like the Bourne node crashed in the middle of the job.)
        // We shouldn't trigger discovery of systems when user trigger the job for first time and the job is already InProgress.
        else if (!scheduler && (systemTime - lastTime > refreshInterval * 1000) && lastTime > 0) {
            _logger.info("User triggered {} Job for {} attempted to schedule later than refresh interval allows. Reschedule the job", type,
                    id);
        } else if (scheduler
                && (systemTime - lastTime > refreshInterval * 1000)
                && nextTime > 0
                && System.currentTimeMillis() - nextTime >= JobIntervals.getMaxIdleInterval() * 1000) {
            _logger.info("Scheduled {} Job for {} was idle for too long. Reschedule the job", type, id);
        }
        else {
            _logger.info("{} Job for {} is in Progress", type, id);
            return false;
        }
        return true;
    }

    /**
     * We would like the refresh interval to be configurable on-the-fly, so we'll check the system properties to see if it's
     * set there.
     * 
     * @param type type of Job Interval
     * @return the value of the system property for that type, otherwise the default configinfo property.
     */
    private long getRefreshInterval(String type) {
        long refreshInterval = JobIntervals.get(type).getRefreshInterval();
        String prop = _coordinator.getPropertyInfo().getProperty(
                PROP_HEADER_CONTROLLER + JobIntervals.get(type)._refreshInterval.replace('-', '_'));
        if (prop != null) {
            refreshInterval = Long.parseLong(prop);
        }
        return refreshInterval;
    }

    private <T extends DiscoveredSystemObject> long getLastRunTime(
            T storageSystem, String type) {
        if (ControllerServiceImpl.METERING.equalsIgnoreCase(type)) {
            return storageSystem.getLastMeteringRunTime();
        } else {
            return storageSystem.getLastDiscoveryRunTime();
        }
    }

    private <T extends DiscoveredSystemObject> long getNextRunTime(
            T storageSystem, String type) {
        if (ControllerServiceImpl.METERING.equalsIgnoreCase(type)) {
            return storageSystem.getNextMeteringRunTime();
        } else {
            return storageSystem.getNextDiscoveryRunTime();
        }
    }

    /**
     * get Status
     * 
     * @param <T>
     * @param system
     * @param type
     * @return
     */
    private <T extends DiscoveredSystemObject> String getStatus(T system, String type) {
        if (ControllerServiceImpl.METERING.equalsIgnoreCase(type)) {
            return system.getMeteringStatus();
        } else {
            return system.getDiscoveryStatus();
        }
    }

    /**
     * Stopping DisocveryConsumer, would close the execService.
     */
    public void stop() {
        try {
            discoverySchedulingSelector.close();
            _dataCollectionExecutorService.shutdown();
            _dataCollectionExecutorService.awaitTermination(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            // To-DO: filter it for timeout sException
            // No need to throw any exception
            _logger.error("TimeOut occured after waiting Client Threads to finish");
        }
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

    public void setConfigInfo(Map<String, String> configInfo) {
        _configInfo = configInfo;
    }

    public Map<String, String> getConfigInfo() {
        return _configInfo;
    }

    public void setConnectionFactory(CIMConnectionFactory cimConnectionFactory) {
        _connectionFactory = cimConnectionFactory;
    }

    public List<URI> refreshProviderConnections() {

        List<URI> activeProviderURIs = new ArrayList<URI>();

        activeProviderURIs.addAll(_connectionFactory.refreshConnections(
                CustomQueryUtility.getActiveStorageProvidersByInterfaceType(
                        _dbClient, StorageProvider.InterfaceType.smis.name())));

        activeProviderURIs.addAll(_connectionFactory.refreshConnections(
                CustomQueryUtility.getActiveStorageProvidersByInterfaceType(
                        _dbClient, StorageProvider.InterfaceType.ibmxiv.name())));

        activeProviderURIs.addAll(VPlexDeviceController.getInstance()
                .refreshConnectionStatusForAllVPlexManagementServers());

        activeProviderURIs.addAll(HDSUtils.refreshHDSConnections(
                CustomQueryUtility.getActiveStorageProvidersByInterfaceType(
                        _dbClient, StorageProvider.InterfaceType.hicommand.name()),
                _dbClient, hdsApiFactory));

        activeProviderURIs.addAll(CinderUtils.refreshCinderConnections(
                CustomQueryUtility.getActiveStorageProvidersByInterfaceType(
                        _dbClient, StorageProvider.InterfaceType.cinder.name()),
                _dbClient));

        activeProviderURIs.addAll(DataDomainUtils.refreshDDConnections(
                CustomQueryUtility.getActiveStorageProvidersByInterfaceType(
                        _dbClient, StorageProvider.InterfaceType.ddmc.name()),
                _dbClient, ddClientFactory));

        activeProviderURIs.addAll(XtremIOProvUtils.refreshXtremeIOConnections(
                CustomQueryUtility.getActiveStorageProvidersByInterfaceType(
                        _dbClient, StorageProvider.InterfaceType.xtremio.name()),
                _dbClient, xioClientFactory));

        activeProviderURIs.addAll(ScaleIOStorageDevice.getInstance().refreshConnectionStatusForAllSIOProviders());

        activeProviderURIs.addAll(CephUtils.refreshCephConnections(
                CustomQueryUtility.getActiveStorageProvidersByInterfaceType(
                        _dbClient, StorageProvider.InterfaceType.ceph.name()),
                _dbClient));

        return activeProviderURIs;
    }

    public void setHdsApiFactory(HDSApiFactory hdsApiFactory) {
        this.hdsApiFactory = hdsApiFactory;
    }

    public void setDataDomainFactory(DataDomainClientFactory ddClientFactory) {
        this.ddClientFactory = ddClientFactory;
    }

    public void setXtremIOFactory(XtremIOClientFactory xioClientFactory) {
        this.xioClientFactory = xioClientFactory;
    }

    /**
     * Sets portMetricsProcess via Spring injection
     * 
     * @param portMetricsProcessor
     */
    public void setPortMetricsProcessor(PortMetricsProcessor portMetricsProcessor) {
        _portMetricsProcessor = portMetricsProcessor;
    }
}
