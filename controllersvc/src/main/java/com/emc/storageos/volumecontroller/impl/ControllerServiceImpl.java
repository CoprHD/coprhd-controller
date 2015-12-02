/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.eclipse.jetty.util.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.emc.storageos.cinder.api.CinderApiFactory;
import com.emc.storageos.coordinator.client.beacon.ServiceBeacon;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.ConnectionStateListener;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedAroundHook;
import com.emc.storageos.coordinator.client.service.DistributedLockQueueManager;
import com.emc.storageos.coordinator.client.service.DistributedQueue;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.client.service.LeaderSelectorListenerForPeriodicTask;
import com.emc.storageos.coordinator.client.service.impl.DistributedLockQueueScheduler;
import com.emc.storageos.coordinator.client.service.impl.LeaderSelectorListenerImpl;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem.Discovery_Namespaces;
import com.emc.storageos.db.common.DataObjectScanner;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.hds.api.HDSApiFactory;
import com.emc.storageos.locking.DistributedOwnerLockServiceImpl;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.StorageSystemViewObject;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.vnxe.VNXeApiClientFactory;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerService;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.impl.job.QueueJob;
import com.emc.storageos.volumecontroller.impl.job.QueueJobSerializer;
import com.emc.storageos.volumecontroller.impl.job.QueueJobTracker;
import com.emc.storageos.volumecontroller.impl.monitoring.MonitoringJob;
import com.emc.storageos.volumecontroller.impl.monitoring.MonitoringJobConsumer;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionDiscoverJob;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionJob;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionJobConsumer;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionJobScheduler;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionJobSerializer;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DiscoverTaskCompleter;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.ScanTaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.smis.ibm.xiv.XIVSmisCommandHelper;
import com.emc.storageos.vplex.api.VPlexApiFactory;
import com.emc.storageos.workflow.WorkflowService;

/**
 * Default controller service implementation
 */
public class ControllerServiceImpl implements ControllerService {

    // constants
    private static final String JOB_QUEUE_NAME = "jobqueue";
    private static final String DISCOVER_JOB_QUEUE_NAME = "discoverjobqueue";
    private static final String COMPUTE_DISCOVER_JOB_QUEUE_NAME = "computediscoverjobqueue";
    private static final String SCAN_JOB_QUEUE_NAME = "scanjobqueue";
    public static final String MONITORING_JOB_QUEUE_NAME = "monitoringjobqueue";
    private static final String METERING_JOB_QUEUE_NAME = "meteringjobqueue";
    public static final String DISCOVERY = "Discovery";
    public static final String DISCOVERY_RECONCILE_TZ = "DiscoveryReconcileTZ";
    public static final String SCANNER = "Scanner";
    public static final String METERING = "Metering";
    public static final String MONITORING = "Monitoring";
    public static final String POOL_MATCHER = "PoolMatcher";
    public static final String NS_DISCOVERY = "NS_Discovery";
    public static final String COMPUTE_DISCOVERY = "Compute_Discovery";
    public static final String CS_DISCOVERY = "CS_Discovery";
    private static final String DISCOVERY_COREPOOLSIZE = "discovery-core-pool-size";
    private static final String COMPUTE_DISCOVERY_COREPOOLSIZE = "compute-discovery-core-pool-size";
    private static final String METERING_COREPOOLSIZE = "metering-core-pool-size";
    private static final int DEFAULT_MAX_THREADS = 100;
    public static final String CONNECTION = "Connection";
    public static final String CAPACITY_COMPUTE_DELAY = "capacity-compute-delay";
    public static final String CAPACITY_COMPUTE_INTERVAL = "capacity-compute-interval";
    public static final String CAPACITY_LEADER_PATH = "capacityleader";
    public static final String CAPACITY_LEADER_NAME = "capacityprocessor";
    public static final String CUSTOM_CONFIG_PATH = "customconfigleader";
    public static final long DEFAULT_CAPACITY_COMPUTE_DELAY = 5;
    public static final long DEFAULT_CAPACITY_COMPUTE_INTERVAL = 3600;

    // list of support discovery job type
    private static final String[] DISCOVERY_JOB_TYPES = new String[] { DISCOVERY, NS_DISCOVERY, CS_DISCOVERY, COMPUTE_DISCOVERY };

    private static final Logger _log = LoggerFactory.getLogger(ControllerServiceImpl.class);
    private Dispatcher _dispatcher;
    private DbClient _dbClient;
    private CoordinatorClient _coordinator;
    private WorkflowService _workflowService;
    private DistributedOwnerLockServiceImpl _distributedOwnerLockService;
    @Autowired
    private CustomConfigHandler customConfigHandler;

    private static volatile DistributedQueue<QueueJob> _jobQueue = null;
    private QueueJobTracker _jobTracker;
    private Map<String, String> _configInfo;
    private static volatile ApplicationContext _context;
    private static volatile DistributedQueue<DataCollectionJob> _discoverJobQueue = null;
    private static volatile DistributedQueue<DataCollectionJob> _computeDiscoverJobQueue = null;
    private static volatile DistributedQueue<DataCollectionJob> _scanJobQueue = null;
    private static volatile DistributedQueue<DataCollectionJob> _meteringJobQueue = null;
    private static volatile DistributedQueue<DataCollectionJob> _monitoringJobQueue = null;
    private CIMConnectionFactory _cimConnectionFactory;
    private VPlexApiFactory _vplexApiFactory;
    private HDSApiFactory hdsApiFactory;
    private CinderApiFactory cinderApiFactory;
    private VNXeApiClientFactory _vnxeApiClientFactory;
    private SmisCommandHelper _helper;
    private XIVSmisCommandHelper _xivSmisCommandHelper;
    private static volatile DataCollectionJobConsumer _scanJobConsumer;
    private static volatile DataCollectionJobConsumer _discoverJobConsumer;
    private static volatile DataCollectionJobConsumer _computeDiscoverJobConsumer;
    private static volatile DataCollectionJobConsumer _meteringJobConsumer;
    private static volatile DataCollectionJobScheduler _jobScheduler;
    private MonitoringJobConsumer _monitoringJobConsumer;
    private ConnectionStateListener zkConnectionStateListenerForMonitoring;
    private DataObjectScanner _doScanner;
    private DistributedLockQueueManager _lockQueueManager;
    private ControlRequestTaskConsumer _controlRequestTaskConsumer;
    private DrUtil _drUtil;

    ManagedCapacityImpl _capacityCompute;
    LeaderSelector _capacityService;

    public static enum Lock {
        SCAN_COLLECTION_LOCK("lock-scancollectionjob-"),
        DISCOVER_COLLECTION_LOCK("lock-discovercollectionjob-"),
        METERING_COLLECTION_LOCK("lock-meteringcollectionjob-"),
        NS_DATA_COLLECTION_LOCK("lock-ns-datacollectionjob-"),
        COMPUTE_DATA_COLLECTION_LOCK("lock-compute-datacollectionjob-"),
        CS_DATA_COLLECTION_LOCK("lock-cs-datacollectionjob-"),
        POOL_MATCHER_LOCK("lock-implicitpoolmatcherjob-"),
        DISCOVER_RECONCILE_TZ_LOCK("lock-discoverreconciletz-");

        private final String _lockName;
        private InterProcessLock _processLock;
        private final long _timeout;

        Lock(String lockName) {
            _lockName = lockName;
            _processLock = null;
            if (_lockName.equals("lock-scancollectionjob-")) {
                _timeout = Constants.SCAN_LOCK_ACQUIRE_TIME;
            } else if (_lockName.equals("lock-discovercollectionjob-") || _lockName.equals("lock-discoverreconciletz-")) {
                _timeout = Constants.DISCOVER_LOCK_ACQUIRE_TIME;
            } else if (_lockName.equals("lock-meteringcollectionjob-")) {
                _timeout = Constants.METERING_LOCK_ACQUIRE_TIME;
            } else if (_lockName.equals("lock-ns-datacollectionjob-")) {
                _timeout = Constants.DISCOVER_LOCK_ACQUIRE_TIME;
            } else if (_lockName.equals("lock-compute-datacollectionjob-")) {
                _timeout = Constants.DISCOVER_LOCK_ACQUIRE_TIME;
            } else if (_lockName.equals("lock-cs-datacollectionjob-")) {
                _timeout = Constants.DISCOVER_LOCK_ACQUIRE_TIME;
            } else if (_lockName.equals("lock-implicitpoolmatcherjob-")) {
                _timeout = Constants.DEFAULT_LOCK_ACQUIRE_TIME;
            } else {
                _timeout = 0;
            }
        }

        public String toString() {
            return _lockName;
        }

        private void setLock(InterProcessLock processLock) {
            _processLock = processLock;
        }

        private InterProcessLock getLock() {
            return _processLock;
        }

        public long getRecommendedTimeout() {
            return _timeout;
        }

        /**
         * Release lock
         * 
         * @throws Exception
         */
        public void release() throws Exception {
            _processLock.release();
        }

        /**
         * Verify, whether this process holds the lock
         * 
         * @return boolean
         */
        public boolean isAcquired() {
            return (_processLock != null) && _processLock.isAcquiredInThisProcess();
        }

        /**
         * Blocking lock acquisition
         * 
         * @throws Exception
         */
        public void acquire() throws Exception {
            _processLock.acquire();
        };

        /**
         * acquire lock - wait at most "time second" for the lock to become available
         * 
         * @param time : number of seconds to wait in the blocking mode untill the lock becomes available.
         *            if not able to get lock
         * 
         * @return boolean
         * @throws Exception
         */
        public boolean acquire(long time) throws Exception {
            return _processLock.acquire(time, TimeUnit.SECONDS);
        }

        public static Lock getLock(String type) {
            if (type.equals(DISCOVERY)) {
                return DISCOVER_COLLECTION_LOCK;
            } else if (type.equals(SCANNER)) {
                return SCAN_COLLECTION_LOCK;
            } else if (type.equals(NS_DISCOVERY)) {
                return NS_DATA_COLLECTION_LOCK;
            } else if (type.equals(COMPUTE_DISCOVERY)) {
                return COMPUTE_DATA_COLLECTION_LOCK;
            } else if (type.equals(CS_DISCOVERY)) {
                return CS_DATA_COLLECTION_LOCK;
            } else if (type.equals(METERING)) {
                return METERING_COLLECTION_LOCK;
            } else if (type.equals(POOL_MATCHER)) {
                return POOL_MATCHER_LOCK;
            } else if (type.equals(DISCOVERY_RECONCILE_TZ)) {
                return DISCOVER_RECONCILE_TZ_LOCK;
            } else {
                // impossible
                return null;
            }
        }
    }

    @Autowired
    private ServiceBeacon _svcBeacon;

    /**
     * Set dispatcher
     * 
     * @param dispatcher
     */
    public void setDispatcher(Dispatcher dispatcher) {
        _dispatcher = dispatcher;
    }

    /**
     * Set db client
     * 
     * @param dbClient
     */
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

    /**
     * Set job tracker
     * 
     * @param tracker
     */
    public void setJobTracker(QueueJobTracker tracker) {
        _jobTracker = tracker;
    }

    /**
     * 
     * inject the CIMConnectionFactory instance.
     * 
     * @param cimConnectionFactory
     *            the cimConnectionFactory to set
     */
    public void setCimConnectionFactory(CIMConnectionFactory cimConnectionFactory) {
        _cimConnectionFactory = cimConnectionFactory;
    }

    /**
     * 
     * inject the VPlex API Factory instance.
     * 
     * @param vplexApiFactory
     *            the vplexApiFactory to set
     */
    public void setVplexApiFactory(VPlexApiFactory vplexApiFactory) {
        _vplexApiFactory = vplexApiFactory;
    }

    /**
     * @param hdsApiFactory the hdsApiFactory to set
     */
    public void setHdsApiFactory(HDSApiFactory hdsApiFactory) {
        this.hdsApiFactory = hdsApiFactory;
    }

    /**
     * 
     * inject the VNXe API Factory instance.
     * 
     * @param vnxeApiClientFactory
     *            the vnxeApiClientFactory to set
     */
    public void setVnxeApiClientFactory(VNXeApiClientFactory vnxeApiClientFactory) {
        _vnxeApiClientFactory = vnxeApiClientFactory;
    }

    /**
     * @param cinderApiFactory the cinderApiFactory to set
     */
    public void setCinderApiFactory(CinderApiFactory cinderApiFactory) {
        this.cinderApiFactory = cinderApiFactory;
    }

    /**
     * Inject the SMIS command helper instance.
     * 
     * @param helper the smis command helper
     */
    public void setSmisCommandHelper(SmisCommandHelper helper) {
        _helper = helper;
    }

    /**
     * Inject the XIV SMIS command helper instance.
     * 
     * @param helper the XIV smis command helper
     */
    public void setXivSmisCommandHelper(XIVSmisCommandHelper xivSmisCommandHelper) {
        _xivSmisCommandHelper = xivSmisCommandHelper;
    }

    /**
     * Set Scan Consumer
     * 
     * @param dataCollectionJobConsumer
     */
    public void setScanJobConsumer(DataCollectionJobConsumer scanJobConsumer) {
        _scanJobConsumer = scanJobConsumer;
    }

    /**
     * Set Scan Consumer
     * 
     * @param dataCollectionJobConsumer
     */
    public void setDiscoverJobConsumer(DataCollectionJobConsumer discoverJobConsumer) {
        _discoverJobConsumer = discoverJobConsumer;
    }

    /**
     * Set Compute Discover Consumer
     * 
     * @param dataCollectionJobConsumer
     */
    public void setComputeDiscoverJobConsumer(DataCollectionJobConsumer computeDiscoverJobConsumer) {
        _computeDiscoverJobConsumer = computeDiscoverJobConsumer;
    }

    public void setMeteringJobConsumer(DataCollectionJobConsumer meteringJobConsumer) {
        _meteringJobConsumer = meteringJobConsumer;
    }

    /**
     * Set Job Scheduler
     * 
     * @param DataCollectionJobScheduler
     */
    public void setJobScheduler(DataCollectionJobScheduler jobScheduler) {
        _jobScheduler = jobScheduler;
    }

    public void setConfigInfo(Map<String, String> configInfo) {
        _configInfo = configInfo;
    }

    public Map<String, String> getConfigInfo() {
        return _configInfo;
    }

    public void setCapacityCompute(ManagedCapacityImpl capacityCompute) {
        _capacityCompute = capacityCompute;
    }

    /**
     * Set DataObjectScanner
     * 
     * @param scanner
     */
    public void setDataObjectScanner(DataObjectScanner scanner) {
        _doScanner = scanner;
    }

    @Override
    public void start() throws Exception {
        _log.info("Starting controller service");
        _dbClient.start();
        // Without this delay, my testing for Workflow functionality indicated
        // that when a Bourne node is restarted with operations that have not
        // completed in the dispatcher, Zookeeper will often come up first,
        // and the Dispatcher will start running tasks (from before the restart)
        // before Cassandra has come up. Those tasks then generally fail again
        // because the first thing they often do is try to retrieve some record(s)
        // from Cassandra, and since it is not up yet, they take a Connection Exception.
        // Watson
        Thread.sleep(30000);        // wait 30 seconds for database to connect
        _log.info("Waiting done");
        _dispatcher.start();

        _jobTracker.setJobContext(new JobContext(_dbClient, _cimConnectionFactory,
                _vplexApiFactory, hdsApiFactory, cinderApiFactory, _vnxeApiClientFactory, _helper, _xivSmisCommandHelper));
        _jobTracker.start();
        _jobQueue = _coordinator.getQueue(JOB_QUEUE_NAME, _jobTracker,
                new QueueJobSerializer(), DEFAULT_MAX_THREADS);
        _workflowService.start();
        _distributedOwnerLockService.start();

        /**
         * Lock used in making Scanning/Discovery mutually exclusive.
         */

        for (Lock lock : Lock.values()) {
            lock.setLock(_coordinator.getLock(lock.toString()));
        }

        /**
         * Discovery Queue, an instance of DistributedQueueImpl in
         * CoordinatorService,which holds Discovery Jobs. On starting
         * discoveryConsumer, a new ScheduledExecutorService is instantiated,
         * which schedules Loading Devices from DB every X minutes.
         */

        _discoverJobConsumer.start();
        _computeDiscoverJobConsumer.start();
        _scanJobConsumer.start();
        _meteringJobConsumer.start();
        _discoverJobQueue = _coordinator.getQueue(DISCOVER_JOB_QUEUE_NAME, _discoverJobConsumer,
                new DataCollectionJobSerializer(), Integer.parseInt(_configInfo.get(DISCOVERY_COREPOOLSIZE)), 200);
        _computeDiscoverJobQueue = _coordinator.getQueue(COMPUTE_DISCOVER_JOB_QUEUE_NAME, _computeDiscoverJobConsumer,
                new DataCollectionJobSerializer(), Integer.parseInt(_configInfo.get(COMPUTE_DISCOVERY_COREPOOLSIZE)), 50000);
        _meteringJobQueue = _coordinator.getQueue(METERING_JOB_QUEUE_NAME, _meteringJobConsumer,
                new DataCollectionJobSerializer(), Integer.parseInt(_configInfo.get(METERING_COREPOOLSIZE)), 200);
        _scanJobQueue = _coordinator.getQueue(SCAN_JOB_QUEUE_NAME, _scanJobConsumer,
                new DataCollectionJobSerializer(), 1, 50);
        /**
         * Monitoring use cases starts here
         */
        _monitoringJobQueue = _coordinator.getQueue(MONITORING_JOB_QUEUE_NAME, _monitoringJobConsumer,
                new DataCollectionJobSerializer(), DEFAULT_MAX_THREADS);

        /**
         * Adds listener class for zk connection state change.
         * This listener will release local CACHE while zk connection RECONNECT.
         */
        _coordinator.setConnectionListener(zkConnectionStateListenerForMonitoring);
        /**
         * Starts Monitoring scheduled thread
         */
        _monitoringJobConsumer.start();

        _jobScheduler.start();

        _svcBeacon.start();

        startLockQueueService();

        startCapacityService();
        loadCustomConfigDefaults();
        checkSiteStateForFailover();
    }

    @Override
    public void stop() throws Exception {
        _log.info("Stopping controller service");
        _controlRequestTaskConsumer.stop();
        _lockQueueManager.stop();
        _jobScheduler.stop();
        _discoverJobQueue.stop(120000);
        _computeDiscoverJobQueue.stop(120000);
        _scanJobQueue.stop(120000);
        _monitoringJobQueue.stop(120000);
        _meteringJobQueue.stop(120000);
        _dispatcher.stop();
        _scanJobConsumer.stop();
        _discoverJobConsumer.stop();
        _computeDiscoverJobConsumer.stop();
        _meteringJobConsumer.stop();
        _monitoringJobConsumer.stop();
        _dbClient.stop();
        /**
         * Extra condition to make sure, the lock is released, at conditions where stop is invoked.
         */
        for (Lock lock : Lock.values()) {
            try {
                if (lock.isAcquired()) {
                    lock.release();
                }
            } catch (Exception e) {
                _log.error("Failed to release data collection lock: " + lock.toString());
            }

        }

        _capacityService.close();
    }

    private void startCapacityService() {
        long delay = DEFAULT_CAPACITY_COMPUTE_DELAY;
        String delay_str = _configInfo.get(CAPACITY_COMPUTE_DELAY);
        if (delay_str != null) {
            delay = Long.parseLong(delay_str);
        }
        long interval = DEFAULT_CAPACITY_COMPUTE_INTERVAL;
        String interval_str = _configInfo.get(CAPACITY_COMPUTE_INTERVAL);
        if (interval_str != null) {
            interval = Long.parseLong(interval_str);
        }
        LeaderSelectorListenerForPeriodicTask executor = new LeaderSelectorListenerForPeriodicTask(_capacityCompute, delay, interval);

        _capacityService = _coordinator.getLeaderSelector(CAPACITY_LEADER_PATH,
                executor);
        _capacityService.autoRequeue();
        _capacityService.start();
    }

    private void startLockQueueService() {
        // Configure coordinator with the owner lock around-hook.
        DistributedAroundHook aroundHook = _distributedOwnerLockService.getDistributedOwnerLockAroundHook();
        _coordinator.setDistributedOwnerLockAroundHook(aroundHook);

        // Start lock queue task consumer, for sending queued requests back to the Dispatcher
        _controlRequestTaskConsumer.start();
        // Gets a started lock queue manager
        _lockQueueManager = _coordinator.getLockQueue(_controlRequestTaskConsumer);

        // Set lock queue manager where appropriate
        _dispatcher.setLockQueueManager(_lockQueueManager);
        _distributedOwnerLockService.setLockQueueManager(_lockQueueManager);

        // Configure how lock queue manager should generate item names.
        _lockQueueManager.setNameGenerator(new ControlRequestLockQueueItemName());

        // Configure lock queue scheduler
        DistributedLockQueueScheduler dlqScheduler = new DistributedLockQueueScheduler();
        dlqScheduler.setCoordinator(_coordinator);
        dlqScheduler.setLockQueue(_lockQueueManager);
        dlqScheduler.setValidator(new OwnerLockValidator(_distributedOwnerLockService));
        dlqScheduler.start();

        // Configure lock queue listeners
        ControlRequestLockQueueListener controlRequestLockQueueListener = new ControlRequestLockQueueListener();
        controlRequestLockQueueListener.setDbClient(_dbClient);
        controlRequestLockQueueListener.setWorkflowService(_workflowService);
        _lockQueueManager.getListeners().add(controlRequestLockQueueListener);
    }

    private void loadCustomConfigDefaults() {
        LeaderSelectorListenerImpl executor = new LeaderSelectorListenerImpl() {
            @Override
            protected void startLeadership() throws Exception {
                customConfigHandler.loadSystemCustomConfigs();
            }

            @Override
            protected void stopLeadership() {
            }
        };

        LeaderSelector service = _coordinator.getLeaderSelector(CUSTOM_CONFIG_PATH,
                executor);
        service.autoRequeue();
        service.start();
    }

    public static void enqueueJob(QueueJob job) throws Exception {
        _jobQueue.put(job);
    }

    public static Object getBean(String name) {
        return _context.getBean(name);
    }

    public static void scheduleDiscoverJobs(AsyncTask[] tasks, Lock lock, String jobType) throws Exception {
        ArrayList<DataCollectionJob> jobs = createDiscoverJobsForTasks(tasks, jobType);
        _jobScheduler.scheduleMultipleJobs(jobs, lock);
    }

    private static ArrayList<DataCollectionJob> createDiscoverJobsForTasks(AsyncTask[] tasks, String jobType) {
        ArrayList<DataCollectionJob> jobs = new ArrayList<DataCollectionJob>();
        for (AsyncTask task : tasks) {
            DiscoverTaskCompleter completer = new DiscoverTaskCompleter(task, jobType);
            if (null == task._namespace) {
                task._namespace = Discovery_Namespaces.ALL.toString();
            }
            DataCollectionJob job = new DataCollectionDiscoverJob(completer, task._namespace);
            jobs.add(job);
        }
        return jobs;
    }

    /**
     * Queueing Discovery Job into Discovery Queue
     * 
     * @param job
     * @throws Exception
     */
    public static void enqueueDataCollectionJob(DataCollectionJob job) throws Exception {
        String jobType = job.getType();
        if (jobType.equals(CS_DISCOVERY)) {
            _computeDiscoverJobQueue.put(job);
        }
        else if (isDiscoveryJobTypeSupported(jobType)) {
            _discoverJobQueue.put(job);
        }
        else if (jobType.equals(SCANNER)) {
            _scanJobQueue.put(job);
        }
        else if (jobType.equals(MONITORING)) {
            _monitoringJobQueue.put(job);
        } else if (jobType.equals(METERING)) {
            _meteringJobQueue.put(job);
        }
        _log.info("Queued " + jobType + " job for " + job.systemString());

    }

    /**
     * Queueing MonitoringJob instance into Monitoring Queue
     * 
     * @param job {@link MonitoringJob} MonitoringJob distributed token
     * @throws Exception
     */
    public static void enqueueMonitoringJob(DataCollectionJob job) throws Exception {
        _monitoringJobQueue.put(job);
        _log.info("Queued Monitoring Job");
    }

    /**
     * Sets workflowService wiring up.
     * 
     * @param workflowService
     */
    public void setWorkflowService(WorkflowService workflowService) {
        this._workflowService = workflowService;
    }

    @Override
    public void setApplicationContext(ApplicationContext context)
            throws BeansException {
        _context = context;
    }

    /**
     * Sets monitoringJobConsumer
     * 
     * @param monitoringJobConsumer {@link MonitoringJobConsumer}
     */
    public void setMonitoringJobConsumer(MonitoringJobConsumer monitoringJobConsumer) {
        _monitoringJobConsumer = monitoringJobConsumer;
    }

    /**
     * Sets zkConnectionStateListenerForMonitoring
     * 
     * @param zkConnectionStateListenerForMonitoring
     */
    public void setZkConnectionStateListenerForMonitoring(
            ConnectionStateListener zkConnectionStateListenerForMonitoring) {
        this.zkConnectionStateListenerForMonitoring = zkConnectionStateListenerForMonitoring;
    }

    public static void performScan(URI provider,
            ScanTaskCompleter scanCompleter,
            Map<String, StorageSystemViewObject> storageCache)
            throws DatabaseException, BaseCollectionException, DeviceControllerException {
        _scanJobConsumer.performScan(provider, scanCompleter, storageCache);
    }

    /**
     * Check whether the given jobType is one of supported discovery job type.
     * 
     * @param jobType
     * @return
     */
    public static boolean isDiscoveryJobTypeSupported(String jobType) {
        return jobType != null && Arrays.asList(DISCOVERY_JOB_TYPES).contains(jobType);
    }

    public DistributedOwnerLockServiceImpl getDistributedOwnerLockService() {
        return _distributedOwnerLockService;
    }

    public void setDistributedOwnerLockService(
            DistributedOwnerLockServiceImpl _distributedOwnerLockService) {
        this._distributedOwnerLockService = _distributedOwnerLockService;
    }

    public void setControlRequestTaskConsumer(ControlRequestTaskConsumer consumer) {
        _controlRequestTaskConsumer = consumer;
    }

    public void setDrUtil(DrUtil drUtil) {
        this._drUtil = drUtil;
    }
    
    private void checkSiteStateForFailover() {
        try {
            Site site = _drUtil.getLocalSite();
            
            if (site.getState().equals(SiteState.STANDBY_FAILING_OVER)) {
                _log.info("Site state is STANDBY_FAILING_OVER, set it to PRIMARY");
                site.setState(SiteState.ACTIVE);
                _coordinator.persistServiceConfiguration(site.toConfiguration());
            }
        } catch (Exception e) {
            _log.error("Failed to check site state for failover");
        }
    }
}
