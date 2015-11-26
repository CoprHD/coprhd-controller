/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.impl;

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.task.TaskScrubberExecutor;
import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.YamlConfigurationLoader;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.service.CassandraDaemon;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.service.StorageServiceMBean;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.config.EncryptionOptions.ServerEncryptionOptions.InternodeEncryption;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.beacon.ServiceBeacon;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DbOfflineEventInfo;
import com.emc.storageos.db.common.DbConfigConstants;
import com.emc.storageos.db.common.DbServiceStatusChecker;
import com.emc.storageos.db.gc.GarbageCollectionExecutor;
import com.emc.storageos.db.server.DbService;
import com.emc.storageos.db.server.MigrationHandler;
import com.emc.storageos.db.server.impl.StartupMode.DbReinitMode;
import com.emc.storageos.db.server.impl.StartupMode.GeodbRestoreMode;
import com.emc.storageos.db.server.impl.StartupMode.HibernateMode;
import com.emc.storageos.db.server.impl.StartupMode.NormalMode;
import com.emc.storageos.db.server.impl.StartupMode.ObsoletePeersCleanupMode;
import com.emc.storageos.services.util.*;
import static com.emc.storageos.services.util.FileUtils.readValueFromFile;
import static com.emc.storageos.services.util.FileUtils.getLastModified;

import org.apache.curator.framework.recipes.locks.InterProcessLock;

/**
 * Default database service implementation
 */
public class DbServiceImpl implements DbService {
    private static final Logger _log = LoggerFactory.getLogger(DbServiceImpl.class);
    private static final String DB_SCHEMA_LOCK = "dbschema";
    private static final String GEODB_SCHEMA_LOCK = "geodbschema";
    private static final String DB_NO_ENCRYPT_FLAG_FILE = "/data/db/no_db_encryption";
    private static final String DB_INITIALIZED_FLAG_FILE = "/var/run/storageos/dbsvc_initialized";
    private static final Integer INIT_LOCAL_DB_NUM_TOKENS = 256;
    private static final Integer INIT_GEO_DB_NUM_TOKENS = 16;

    public static DbServiceImpl instance = null;

    // run failure detector every 5 min by default
    private static final int DEFAULT_DETECTOR_RUN_INTERVAL_MIN = 5;
    private int _detectorInterval = DEFAULT_DETECTOR_RUN_INTERVAL_MIN;

    // Service outage time should be less than 5 days, or else service will not be allowed to get started any more.
    // As we checked the downtime every 15 mins, to avoid actual downtime undervalued, setting the max value as 4 days.
    private static final long MAX_SERVICE_OUTAGE_TIME = 4 * TimeUtils.DAYS;
    private AlertsLogger alertLog = AlertsLogger.getAlertsLogger();

    private String _config;
    private CoordinatorClient _coordinator;
    private CassandraDaemon _service;
    private SchemaUtil _schemaUtil;
    private MigrationHandler _handler;
    private GarbageCollectionExecutor _gcExecutor;
    private TaskScrubberExecutor _taskScrubber;
    // 3 threads two threads for node repair, one is for failure detector
    private static final String POOL_NAME = "DBBackgroundPool";
    private ScheduledExecutorService _exe = new NamedScheduledThreadPoolExecutor(POOL_NAME, 3);
    protected Service _serviceInfo;
    private JmxServerWrapper _jmxServer;
    private DbClientImpl _dbClient;
    private ServiceBeacon _svcBeacon;
    private DbServiceStatusChecker _statusChecker;
    // db directory
    private String dbDir;
    private String keystorePath;
    private String truststorePath;
    private boolean cassandraInitialized = false;
    private boolean disableScheduledDbRepair = false;

    @Autowired
    private DbManager dbMgr;

    /**
     * Set db client
     */
    public void setDbClient(DbClientImpl dbClient) {
        _dbClient = dbClient;
    }

    /**
     * Set coordinator client
     * 
     */
    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    /**
     * Set DB schema utility
     * 
     * @param schemaUtil
     */
    public void setSchemaUtil(SchemaUtil schemaUtil) {
        _schemaUtil = schemaUtil;
    }

    public void setMigrationHandler(MigrationHandler handler) {
        _handler = handler;
    }

    /**
     * Service setter
     * 
     * @param service
     *            service info
     */
    public void setService(final Service service) {
        _serviceInfo = service;
    }

    /**
     * Set database config file. It must be in URI form or file must be
     * be in classpath
     * 
     * @param config database config file
     */
    public void setConfig(String config) {
        _config = config;
    }

    /**
     * JMX server wrapper
     */
    public void setJmxServerWrapper(JmxServerWrapper jmxServer) {
        _jmxServer = jmxServer;
    }

    public void setGarbageCollector(GarbageCollectionExecutor gcExecutor) {
        _gcExecutor = gcExecutor;
    }

    public TaskScrubberExecutor getTaskScrubber() {
        return _taskScrubber;
    }

    public void setTaskScrubber(TaskScrubberExecutor taskScrubber) {
        this._taskScrubber = taskScrubber;
    }

    public void setBeacon(ServiceBeacon beacon) {
        _svcBeacon = beacon;
    }

    @Autowired
    public void setStatusChecker(DbServiceStatusChecker statusChecker) {
        _statusChecker = statusChecker;
    }

    public void setDbDir(String dbDir) {
        this.dbDir = dbDir;
    }

    public String getDbDir() {
        return this.dbDir;
    }

    public void setDisableScheduledDbRepair(boolean disableScheduledDbRepair) {
        this.disableScheduledDbRepair = disableScheduledDbRepair;
    }

    /**
     * Check if it is GeoDbSvc
     * 
     * @return
     */
    private boolean isGeoDbsvc() {
        return _schemaUtil.isGeoDbsvc();
    }

    /**
     * Get schema lock name using by current service.
     * 
     * @return
     */
    private String getSchemaLockName() {
        return isGeoDbsvc() ? GEODB_SCHEMA_LOCK : DB_SCHEMA_LOCK;
    }

    public String getConfigValue(String key) {
        String configKind = _coordinator.getDbConfigPath(_serviceInfo.getName());
        Configuration config = _coordinator.queryConfiguration(configKind,
                _serviceInfo.getId());
        if (config != null) {
            return config.getConfig(key);
        }
        return null;
    }

    public void setConfigValue(String key, String value) {
        String configKind = _coordinator.getDbConfigPath(_serviceInfo.getName());
        Configuration config = _coordinator.queryConfiguration(configKind,
                _serviceInfo.getId());
        if (config != null) {
            config.setConfig(key, value);
            _coordinator.persistServiceConfiguration(config);
        }
    }

    /**
     * Checks and registers db configuration information,
     * this is one time when cluster is coming up for the first time
     */
    private Configuration checkConfiguration() {
        String configKind = _coordinator.getDbConfigPath(_serviceInfo.getName());
        Configuration config = _coordinator.queryConfiguration(configKind,
                _serviceInfo.getId());
        if (config == null) {
            // this is a new node
            // 1. register its configuration with coordinator
            // 2. assume autobootstrap configuration
            // this means that when a node is added, it take 1/2 of biggest token rage and
            // copies its data over
            ConfigurationImpl cfg = new ConfigurationImpl();
            cfg.setId(_serviceInfo.getId());
            cfg.setKind(configKind);
            cfg.setConfig(DbConfigConstants.NODE_ID, _coordinator.getInetAddessLookupMap().getNodeId());
            cfg.setConfig(DbConfigConstants.AUTOBOOT, Boolean.TRUE.toString());

            // Adding "num_tokens" and "num_token_ver" for new deployment, which directly reflects the configuration in yaml, so
            // no adjustNumTokens() will be called.
            // If this node is upgraded from an older version, the db-# nodes already exists, which will missing those 2 values,
            // that will lead to an adjustNumTokens() call from syssvc.
            try {
                Config yaml = new YamlConfigurationLoader().loadConfig();
                cfg.setConfig(DbConfigConstants.NUM_TOKENS_KEY, Integer.toString(yaml.num_tokens));
                _log.info("num_tokens for current node should be {} in ZK", yaml.num_tokens);
            } catch (ConfigurationException e) {
                _log.error("Failed to load yaml file", e);
                cfg.setConfig(DbConfigConstants.NUM_TOKENS_KEY, DbConfigConstants.DEFUALT_NUM_TOKENS.toString());
            }
            // check other existing db nodes
            List<Configuration> configs = _coordinator.queryAllConfiguration(configKind);
            if (configs.isEmpty()) {
                // we are the first node - turn off autobootstrap
                cfg.setConfig(DbConfigConstants.AUTOBOOT, Boolean.FALSE.toString());
            }
            // persist configuration
            _coordinator.persistServiceConfiguration(cfg);
            config = cfg;
        } else if (config.getConfig(DbConfigConstants.DB_IP) != null) {
            config.removeConfig(DbConfigConstants.DB_IP);
            config.setConfig(DbConfigConstants.NODE_ID, _coordinator.getInetAddessLookupMap().getNodeId());
            _coordinator.persistServiceConfiguration(config);
        }
        return config;
    }

    private void removeStaleConfiguration() {
        removeStaleServiceConfiguration();
        removeStaleVersionedDbConfiguration();
    }

    private void removeStaleVersionedDbConfiguration() {
        String configKind = _coordinator.getVersionedDbConfigPath(_serviceInfo.getName(), _serviceInfo.getVersion());
        List<Configuration> configs = _coordinator.queryAllConfiguration(configKind);
        for (Configuration config : configs) {
            if (isStaleConfiguration(config)) {
                _coordinator.removeServiceConfiguration(config);
                _log.info("Remove stale version db config, id: {}", config.getId());
            }
        }
    }

    private void removeStaleServiceConfiguration() {
        String configKind = _coordinator.getDbConfigPath(_serviceInfo.getName());
        List<Configuration> configs = _coordinator.queryAllConfiguration(configKind);
        for (Configuration config : configs) {
            if (isStaleConfiguration(config)) {
                _coordinator.removeServiceConfiguration(config);
                _log.info("Remove stale config, id: {}", config.getId());
            }
        }
    }

    private boolean isStaleConfiguration(Configuration config) {
        String delimiter = "-";
        String configId = config.getId();

        // Bypasses item of "global" and folders of "version", just check db configurations.
        if (configId == null || configId.equals(Constants.GLOBAL_ID) || !configId.contains(delimiter)) {
            return false;
        }

        if (_serviceInfo.getId().endsWith(Constants.STANDALONE_ID)) {
            if (!configId.equals(_serviceInfo.getId())) {
                return true;
            }
        } else {
            CoordinatorClientInetAddressMap nodeMap = _coordinator.getInetAddessLookupMap();
            int nodeCount = nodeMap.getControllerNodeIPLookupMap().size();

            String nodeIndex = configId.split(delimiter)[1];
            if (Constants.STANDALONE_ID.equalsIgnoreCase(nodeIndex) || Integer.parseInt(nodeIndex) > nodeCount) {
                return true;
            }
        }
        return false;
    }

    // check and initialize global configuration
    private Configuration checkGlobalConfiguration() {
        String configKind = _coordinator.getDbConfigPath(_serviceInfo.getName());
        Configuration config = _coordinator.queryConfiguration(configKind, Constants.GLOBAL_ID);
        if (config == null) {
            ConfigurationImpl cfg = new ConfigurationImpl();
            cfg.setId(Constants.GLOBAL_ID);
            cfg.setKind(configKind);

            // persist configuration
            _coordinator.persistServiceConfiguration(cfg);
            config = cfg;
        }
        return config;
    }

    // check and initialize versioned configuration
    private Configuration checkVersionedConfiguration() {
        String serviceVersion = _serviceInfo.getVersion();
        String dbSchemaVersion = _dbClient.getSchemaVersion();
        if (!serviceVersion.equals(dbSchemaVersion)) {
            _log.warn("The db service version {} doesn't equals Db schema version {}, " +
                    "set db service version to Db schema version",
                    serviceVersion, dbSchemaVersion);
            _serviceInfo.setVersion(dbSchemaVersion);
        }

        String kind = _coordinator.getVersionedDbConfigPath(_serviceInfo.getName(), _serviceInfo.getVersion());
        Configuration config = _coordinator.queryConfiguration(kind,
                _serviceInfo.getId());
        if (config == null) {
            ConfigurationImpl cfg = new ConfigurationImpl();
            cfg.setId(_serviceInfo.getId());
            cfg.setKind(kind);
            // persist configuration
            _coordinator.persistServiceConfiguration(cfg);
            config = cfg;
        }
        return config;
    }

    /**
     * Check offline event info to see if dbsvc/geodbsvc on this node could get started
     */
    private void checkDBOfflineInfo() {
        Configuration config = _coordinator.queryConfiguration(Constants.DB_DOWNTIME_TRACKER_CONFIG,
                _serviceInfo.getName());
        DbOfflineEventInfo dbOfflineEventInfo = new DbOfflineEventInfo(config);

        String localNodeId = _coordinator.getInetAddessLookupMap().getNodeId();
        Long lastActiveTimestamp = dbOfflineEventInfo.geLastActiveTimestamp(localNodeId);
        long zkTimeStamp = (lastActiveTimestamp == null) ? TimeUtils.getCurrentTime() : lastActiveTimestamp;

        File localDbDir = new File(dbDir);
        boolean isDirEmpty = localDbDir.list().length == 0;
        long localTimeStamp = (isDirEmpty) ? TimeUtils.getCurrentTime() : getLastModified(localDbDir).getTime();

        _log.info("Service timestamp in ZK is {}, local file is: {}", zkTimeStamp, localTimeStamp);
        long diffTime = (zkTimeStamp > localTimeStamp) ? (zkTimeStamp - localTimeStamp) : 0;
        if (diffTime >= MAX_SERVICE_OUTAGE_TIME) {
            String errMsg = String.format("We detect database files on local disk are more than %s days older " +
                    "than last time it was seen in the cluster. It may bring stale data into the database, " +
                    "so the service cannot continue to boot. It may be the result of a VM snapshot rollback. " +
                    "Please contact with EMC support engineer for solution.", diffTime/TimeUtils.DAYS);
            alertLog.error(errMsg);
            throw new IllegalStateException(errMsg);
        }

        Long offlineTime = dbOfflineEventInfo.getOfflineTimeInMS(localNodeId);
        if (offlineTime != null && offlineTime >= MAX_SERVICE_OUTAGE_TIME) {
            String errMsg = String.format("This node is offline for more than %s days. It may bring stale data into " +
                    "database, so the service cannot continue to boot. Please poweroff this node and follow our " +
                    "node recovery procedure to recover this node", offlineTime/TimeUtils.DAYS);
            alertLog.error(errMsg);
            throw new IllegalStateException(errMsg);
        }
    }

    /**
     * Checks and sets INIT_DONE state
     * this means we are done with the actual cf changes on the cassandra side for the target version
     */
    private void setDbConfigInitDone() {
        String configKind = _coordinator.getVersionedDbConfigPath(_serviceInfo.getName(), _serviceInfo.getVersion());
        Configuration config = _coordinator.queryConfiguration(configKind,
                _serviceInfo.getId());
        if (config != null) {
            if (config.getConfig(DbConfigConstants.INIT_DONE) == null) {
                config.setConfig(DbConfigConstants.INIT_DONE, Boolean.TRUE.toString());
                _coordinator.persistServiceConfiguration(config);
            }
        } else {
            // we are expecting this to exist, because its initialized from checkVersionedConfiguration
            throw new IllegalStateException("unexpected error, db versioned configuration is null");
        }
    }

    /**
     * Initializes the keystore/truststore if the paths have been provided.
     */
    private void initKeystoreAndTruststore() {
        try {
            DbClientContext ctx = _dbClient.getLocalContext();

            if (isGeoDbsvc()) {
                ctx = _dbClient.getGeoContext();
            }

            String keystorePath = ctx.getKeyStoreFile();
            String truststorePath = ctx.getTrustStoreFile();

            if (keystorePath == null && truststorePath == null) {
                _log.info("Skipping keystore/truststore initialization, no paths provided");
                return;
            }

            String password = ctx.getTrustStorePassword();
            CassandraKeystoreHandler keystoreHandler = new CassandraKeystoreHandler(_coordinator, keystorePath, truststorePath, password);

            if (keystorePath != null) {
                _log.info("Initializing keystore for current node: {}", keystorePath);
                keystoreHandler.saveKeyStore();
            } else {
                _log.info("Skipping keystore initialization, no path provided");
            }

            if (truststorePath != null) {
                _log.info("Initializing truststore for current node: {}", truststorePath);
                keystoreHandler.saveTrustStore();
            } else {
                _log.info("Skipping truststore initialization, no path provided");
            }
        } catch (Exception e) {
            _log.error("Unexpected exception during initializing cassandra keystore", e);
            throw new IllegalStateException(e);
        }
    }

    private boolean isDbCurrentVersionEncrypted() {
        String currentDbVersion = _coordinator.getCurrentDbSchemaVersion();

        /*
         * This is first boot of fresh install,CurrentDbSchemaVersion has not set yet.
         */
        if (currentDbVersion == null) {
            return true;
        }

        if (currentDbVersion.startsWith("1.") || // Vipr 1.x
                currentDbVersion.startsWith("2.0") || // Vipr 2.0.x
                currentDbVersion.startsWith("2.1")) { // Vipr 2.1.x
            return false;
        }

        return true;
    }

    /**
     * We need to turn off encryption if upgrade from 1.*,2.*,2.1 to higher version for dbsvc because
     * we enable db encryption since 2.2, otherwise first reboot node can't communicate with others .
     */
    private void setEncryptionOptions() {
        InternodeEncryption encryption = null;
        if (isGeoDbsvc()) {
            _log.info("Geo Db, set encryption option to dc");
            encryption = InternodeEncryption.dc;
        } else {
            _log.info("Migration from version which doesn't enable encryption,Disable encryption");
            encryption = InternodeEncryption.none;
            setDisableDbEncryptionFlag();
        }
        DatabaseDescriptor.getServerEncryptionOptions().internode_encryption = encryption;
    }

    private boolean setDisableDbEncryptionFlag() {
        File dbEncryptFlag = new File(DB_NO_ENCRYPT_FLAG_FILE);
        try {
            if (!dbEncryptFlag.exists()) {
                new FileOutputStream(dbEncryptFlag).close();
            }
        } catch (Exception e) {
            _log.error("Failed to create file {} e=", dbEncryptFlag.getName(), e);
            return false;
        }
        return true;
    }

    /**
     * Use a db initialized flag file to block the peripheral services from starting.
     * This gurantees CPU cyles for the core services during boot up.
     */
    protected void setDbInitializedFlag() {
        // set the flag file only for dbsvc (not for geodbsvc) since it always uses more time to 
        // complete comparing to the other
        if (isGeoDbsvc())
            return;

        File dbInitializedFlag = new File(DB_INITIALIZED_FLAG_FILE);
        try {
            if (!dbInitializedFlag.exists()) {
                new FileOutputStream(dbInitializedFlag).close();
            }
        } catch (Exception e) {
            _log.error("Failed to create file {} e=", dbInitializedFlag.getName(), e);
        }
    }

    @Override
    public void start() throws IOException {
        if (_log.isInfoEnabled()) {
            _log.info("Starting DB service...");
        }

        // Suppress Sonar violation of Lazy initialization of static fields should be synchronized
        // start() method will be only called one time when startup dbsvc, so it's safe to ignore sonar violation
        instance = this; // NOSONAR ("squid:S2444")

        initKeystoreAndTruststore();
        System.setProperty("cassandra.config", _config);
        System.setProperty("cassandra.config.loader", CassandraConfigLoader.class.getName());

        InterProcessLock lock = null;
        Configuration config = null;
        boolean schemaInited = false;

        try {
            // we use this lock to discourage more than one node bootstrapping / joining at the same time
            // Cassandra can handle this but it's generally not recommended to make changes to schema concurrently
            lock = getLock(getSchemaLockName());

            config = checkConfiguration();
            checkGlobalConfiguration();
            checkVersionedConfiguration();
            removeStaleConfiguration();

            // The num_tokens in ZK is what we previously running at, which could be different from in current .yaml
            checkNumTokens(config);
            StartupMode mode = checkStartupMode(config);
            _log.info("Current startup mode is {}", mode);

            // Check if service is allowed to get started by querying db offline info to avoid bringing back stale data.
            // Skipping hibernate mode for node recovery procedure to recover the overdue node.
            if (mode.type != StartupMode.StartupModeType.HIBERNATE_MODE) {
                checkDBOfflineInfo();
            }

            // this call causes instantiation of a seed provider instance, so the check*Configuration
            // calls must be preceed it
            removeCassandraSavedCaches();

            mode.onPreStart();

            if (_jmxServer != null) {
                _jmxServer.start();
            }

            if (!isDbCurrentVersionEncrypted() && !_statusChecker.isMigrationDone()) {
                setEncryptionOptions();
            }

            _service = new CassandraDaemon();
            _service.init(null);
            _service.start();

            cassandraInitialized = true;
            mode.onPostStart();
        } catch (Exception e) {
            _log.error("e=", e);
            throw new IllegalStateException(e);
        } finally {
            if (lock != null) {
                try {
                    lock.release();
                } catch (Exception ignore) {
                    _log.debug("lock release failed");
                }
            }
        }

        if (config.getConfig(DbConfigConstants.JOINED) == null) {
            config.setConfig(DbConfigConstants.JOINED, Boolean.TRUE.toString());
            _coordinator.persistServiceConfiguration(config);
        }

        _statusChecker.waitForAllNodesJoined();

        _svcBeacon.start();
        setDbInitializedFlag();
        setDbConfigInitDone();

        _dbClient.start();

        // Setup the vdc information, so that login enabled before migration
        if (!isGeoDbsvc()) {
            _schemaUtil.checkAndSetupBootStrapInfo(_dbClient);
        }

        if (_handler.run()) {
            // Setup the bootstrap info root tenant, if root tenant migrated from local db, then skip it
            if (isGeoDbsvc()) {
                _schemaUtil.checkAndSetupBootStrapInfo(_dbClient);
            }

            // Start dbsvc background tasks
            startBackgroundTasks();
            _log.info("DB service started");
        } else {
            _log.error("DB migration failed. Skipping starting background tasks.");
        }
    }

    private InterProcessLock getLock(String name) throws Exception {
        InterProcessLock lock = null;
        while (true) {
            try {
                lock = _coordinator.getLock(name);
                lock.acquire();
                break; // got lock
            } catch (Exception e) {
                if (_coordinator.isConnected()) {
                    throw e;
                }
            }
        }
        return lock;
    }

    /**
     * Check Cassandra num_tokens settting in ZK.
     */
    private void checkNumTokens(Configuration config) {
        String numTokensEffective = config.getConfig(DbConfigConstants.NUM_TOKENS_KEY);
        if (numTokensEffective == null) {
            numTokensEffective = String.valueOf(isGeoDbsvc() ? INIT_GEO_DB_NUM_TOKENS : INIT_LOCAL_DB_NUM_TOKENS);
        }

        System.setProperty(CassandraConfigLoader.SYSPROP_NUM_TOKENS, numTokensEffective);
        _log.info("Effective Cassandra num of tokens {}", numTokensEffective);
    }

    /**
     * Check startup mode on disk. Startup mode is specified by a property file on disk ${dbdir}/startupmode
     * 
     * @param config
     *            The Confiugration instance
     * @return BootMode instance if detected, null for no on-disk startup mode
     */
    private StartupMode checkStartupModeOnDisk(Configuration config) throws IOException {
        String modeType = readStartupModeFromDisk();
        if (modeType != null) {
            if (Constants.STARTUPMODE_HIBERNATE.equalsIgnoreCase(modeType)) {
                HibernateMode mode = new HibernateMode(config);
                mode.setCoordinator(_coordinator);
                mode.setSchemaUtil(_schemaUtil);
                mode.setDbDir(dbDir);
                return mode;
            } else if (Constants.STARTUPMODE_RESTORE_REINIT.equalsIgnoreCase(modeType)) {
                _log.info("GeodbRestore startup mode found. Current vdc list {}", _schemaUtil.getVdcList().size());
                if (isGeoDbsvc() && _schemaUtil.getVdcList().size() > 1) {
                    GeodbRestoreMode mode = new GeodbRestoreMode(config);
                    mode.setCoordinator(_coordinator);
                    mode.setSchemaUtil(_schemaUtil);
                    mode.setDbDir(dbDir);
                    return mode;
                }
            } else {
                throw new IllegalStateException("Unexpected startup mode " + modeType);
            }
        }
        return null;
    }

    public String readStartupModeFromDisk() throws IOException {
        File startupModeFile = new File(dbDir, Constants.STARTUPMODE);
        String modeType = readValueFromFile(startupModeFile, Constants.STARTUPMODE);
        _log.info("On disk startup mode found {}", modeType);
        return modeType;
    }

    /**
     * Remove startup mode flag on disk
     */
    protected void removeStartupModeOnDisk() {
        _log.info("Remove bootmode file");
        File bootModeFile = new File(dbDir, Constants.STARTUPMODE);
        bootModeFile.delete();
    }

    /**
     * Read bool value from given db config
     * 
     * @param config
     * @param name
     * @return
     */
    private boolean checkConfigBool(Configuration config, String name) {
        String value = config.getConfig(name);
        return value != null && Boolean.parseBoolean(value);
    }

    /**
     * Read a string list(connected by ',') from given db config
     * 
     * @param config
     * @return
     */
    private List<String> checkConfigList(Configuration config, String name) {
        String peerIPs = config.getConfig(name);
        ArrayList<String> peers = new ArrayList<String>();
        if (peerIPs != null) {
            for (String ip : StringUtils.split(peerIPs, ",")) {
                peers.add(ip);
            }
        }
        return peers;
    }

    /**
     * Determine current startup mode. See BootMode for detailed explanation
     * of each mode.
     * 
     * @param config
     * @return
     */
    private StartupMode checkStartupMode(Configuration config) throws IOException {
        // Check on disk mode first
        StartupMode bootMode = checkStartupModeOnDisk(config);
        if (bootMode != null) {
            return bootMode;
        }

        // Check geodb restore flag in zk
        if (checkConfigBool(config, Constants.STARTUPMODE_RESTORE_REINIT)) {
            _log.info("Found geodbrestore config: {}", Constants.STARTUPMODE_RESTORE_REINIT);
            GeodbRestoreMode mode = new GeodbRestoreMode(config);
            mode.setCoordinator(_coordinator);
            mode.setSchemaUtil(_schemaUtil);
            mode.setDbDir(dbDir);
            return mode;
        }

        // Check geodb reinit ZK flag for add-vdc
        if (checkConfigBool(config, Constants.REINIT_DB)) {
            _log.info("Found reinit config: {}", Constants.REINIT_DB);
            // reinit both system table and StorageOS tables
            DbReinitMode mode = new DbReinitMode(config);
            mode.setCoordinator(_coordinator);
            mode.setSchemaUtil(_schemaUtil);
            mode.setDbDir(dbDir);
            return mode;
        }

        // check geodb cleanup mode for remove-vdc
        List<String> obsoletePeers = checkConfigList(config, Constants.OBSOLETE_CASSANDRA_PEERS);
        if (!obsoletePeers.isEmpty()) {
            // drop peers ip/tokens from system table
            ObsoletePeersCleanupMode mode = new ObsoletePeersCleanupMode(config);
            mode.setCoordinator(_coordinator);
            mode.setSchemaUtil(_schemaUtil);
            mode.setObsoletePeers(obsoletePeers);
            return mode;
        } else {
            NormalMode mode = new NormalMode(config);
            mode.setCoordinator(_coordinator);
            mode.setSchemaUtil(_schemaUtil);
            return mode;
        }
    }

    /**
     * Kick off background jobs
     */
    private void startBackgroundTasks() {
        if (!disableScheduledDbRepair) {
            startBackgroundNodeRepairTask();
        }

        if (_gcExecutor != null) {
            _gcExecutor.setDbServiceId(_serviceInfo.getId());
            _gcExecutor.start();
        }

        if (_taskScrubber != null) {
            _taskScrubber.start();
        }

        startBackgroundDetectorTask();
    }

    /**
     * Start the node repair task in background
     */
    private void startBackgroundNodeRepairTask() {
        this.dbMgr.start();
    }

    /**
     * Start the detector task to monitor Cassandra events.
     * When Cassandra encounter internal exception or FS error, it will stop Gossip and RPC,
     * watch such events so that dbsvc could recover from Cassandra internal exception via restart
     * TODO: include other meaningful stats into consideration like memory usage, etc..
     */
    private void startBackgroundDetectorTask() {

        /* start after _detectorInterval 5 mins by default */
        _exe.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    _log.debug("Starting failure detector");

                    StorageServiceMBean svc = null;
                    svc = StorageService.instance;
                    boolean isRPCRunning = svc.isRPCServerRunning();
                    boolean isGossipEnabled = svc.isInitialized();

                    _log.debug("Thrift status = " + isRPCRunning + ", gossip status = " + isGossipEnabled);

                    if (!isRPCRunning && !isGossipEnabled) {
                        _log.info("Thrift RPC and Gossip both stopped on this node");

                        _log.error("Cassandra service stopped unexpectedly, stopping dbsvc forcely ...");
                        /*
                         * As Gossip and RPC stopped, we are not able to flush table out before exit
                         */
                        System.exit(1);
                    }
                    _log.debug("End failure detector");

                } catch (Exception e) {
                    _log.warn("Unexpected exception during cassandra failure detect", e);
                }
            }
        }, _detectorInterval, _detectorInterval, TimeUnit.MINUTES);
    }

    /*
     * Cassandra saved caches would occasionally get corrupted after the reboot, and then
     * dbsvc will fail to start due to the error of OOM. Delete these files before the start
     * of dbsvc to avoid this issue, and these files could be rebuilt afterwards.
     * we should elminate this trick update after Cassandra solve this issue in future.
     */
    private void removeCassandraSavedCaches() {
        _log.info("Try to remove cassandra saved caches");
        String savedCachesLocation = DatabaseDescriptor.getSavedCachesLocation();
        File savedCachesDir = new File(savedCachesLocation);
        if (savedCachesDir != null && savedCachesDir.exists()) {
            for (File file : savedCachesDir.listFiles()) {
                FileUtils.deleteQuietly(file);
            }
            _log.info("Delete cassandra saved caches({}) successfully", savedCachesLocation);
        }
    }

    @Override
    public void stop() {
        stop(false);
    }

    @Override
    public void stopWithDecommission() {
        stop(true);
    }

    private void stop(Boolean decommission) {
        if (_log.isInfoEnabled()) {
            _log.info("Stopping DB service...");
        }

        if (_gcExecutor != null) {
            _gcExecutor.stop();
        }

        if (decommission && cassandraInitialized) {
            flushCassandra();
        }

        _exe.shutdownNow();

        if (cassandraInitialized) {
            _service.stop();
        }

        if (_jmxServer != null) {
            _jmxServer.stop();
        }

        if (_log.isInfoEnabled()) {
            _log.info("DB service stopped...");
        }
    }

    /**
     * Shut down gossip/thrift and then drain
     */
    private void flushCassandra() {
        StorageServiceMBean svc = StorageService.instance;

        if (svc.isInitialized()) {
            svc.stopGossiping();
        }

        if (svc.isRPCServerRunning()) {
            svc.stopRPCServer();
        }

        try {
            svc.drain();
        } catch (Exception e) {
            _log.error("Fail to drain:", e);
        }

    }
}
