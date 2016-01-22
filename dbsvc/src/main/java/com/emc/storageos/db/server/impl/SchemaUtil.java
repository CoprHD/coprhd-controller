/*
 * Copyright (c) 2013-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.impl;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.CassandraOperationType;
import com.netflix.astyanax.Cluster;
import com.netflix.astyanax.KeyspaceTracerFactory;
import com.netflix.astyanax.connectionpool.ConnectionContext;
import com.netflix.astyanax.connectionpool.ConnectionPool;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.connectionpool.exceptions.OperationException;
import com.netflix.astyanax.ddl.ColumnFamilyDefinition;
import com.netflix.astyanax.ddl.KeyspaceDefinition;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.shallows.EmptyKeyspaceTracerFactory;
import com.netflix.astyanax.thrift.AbstractOperationImpl;
import com.netflix.astyanax.thrift.ddl.ThriftColumnFamilyDefinitionImpl;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.locator.IEndpointSnitch;
import org.apache.cassandra.thrift.Cassandra;
import org.apache.commons.lang.StringUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.MigrationStatus;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.impl.CompositeColumnNameSerializer;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.IndexColumnNameSerializer;
import com.emc.storageos.db.client.impl.TimeSeriesType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.LongMap;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.PasswordHistory;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VdcVersion;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.common.DataObjectScanner;
import com.emc.storageos.db.common.DbConfigConstants;
import com.emc.storageos.db.common.DbSchemaInterceptorImpl;
import com.emc.storageos.db.common.DbServiceStatusChecker;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.security.password.PasswordUtils;

/**
 * Utility class for initializing DB schema from model classes
 */
public class SchemaUtil {
    private static final Logger _log = LoggerFactory.getLogger(SchemaUtil.class);
    private static final String COMPARATOR_PACKAGE = "org.apache.cassandra.db.marshal.";
    private static final String DB_BOOTSTRAP_LOCK = "dbbootstrap";
    private static final String VDC_NODE_PREFIX = "node";
    private static final String GEODB_BOOTSTRAP_LOCK = "geodbbootstrap";

    private static final int DEFAULT_REPLICATION_FACTOR = 1;
    private static final int MAX_REPLICATION_FACTOR = 5;
    private static final int DBINIT_RETRY_INTERVAL = 5;
    private static final int DBINIT_RETRY_MAX = 20;

    private String _clusterName = DbClientContext.LOCAL_CLUSTER_NAME;
    private String _keyspaceName = DbClientContext.LOCAL_KEYSPACE_NAME;

    private CoordinatorClient _coordinator;
    private Service _service;
    private DataObjectScanner _doScanner;
    private DbServiceStatusChecker _statusChecker;
    private String _vdcShortId;
    private StringMap _vdcHosts;
    private String _vdcEndpoint;
    private List<String> _vdcList; // List of all joined vdc
    private Properties _dbCommonInfo;
    private PasswordUtils _passwordUtils;
    private DbClientContext clientContext;
    private boolean onStandby = false;
    private DrUtil drUtil;
    private Boolean backCompatPreYoda = false;

    @Autowired
    private DbRebuildRunnable dbRebuildRunnable;

    public void setClientContext(DbClientContext clientContext) {
        this.clientContext = clientContext;
    }

    /**
     * Set service info
     *
     * @param service
     */
    public void setService(Service service) {
        _service = service;
    }

    /**
     * Set coordinator client
     *
     * @param coordinator
     */
    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    /**
     * Return true if current ViPR is standby mode
     *
     * @return
     */
    public boolean isStandby() {
        return onStandby;
    }

    /**
     * Set DataObjectScanner
     *
     * @param scanner
     */
    public void setDataObjectScanner(DataObjectScanner scanner) {
        _doScanner = scanner;
    }

    @Autowired
    public void setStatusChecker(DbServiceStatusChecker statusChecker) {
        _statusChecker = statusChecker;
    }

    /**
     * Set keyspace name
     *
     * @param keyspaceName
     */
    public void setKeyspaceName(String keyspaceName) {
        _keyspaceName = keyspaceName;
    }

    public String getKeyspaceName() {
        return _keyspaceName;
    }

    /**
     * Set cluster name
     *
     * @param clusterName
     */
    public void setClusterName(String clusterName) {
        _clusterName = clusterName;
    }

    /**
     * Set the vdc id of current site. Must have for geodbsvc
     *
     * @param vdcId the vdc id of current site
     */
    public void setVdcShortId(String vdcId) {
        _vdcShortId = vdcId;
    }

    /**
     * Set the endpoint of current vdc, for example, vip
     *
     * @param vdcEndpoint vdc end point
     */
    public void setVdcEndpoint(String vdcEndpoint) {
        _vdcEndpoint = vdcEndpoint;
    }

    public void setDbCommonInfo(Properties dbCommonInfo) {
        _dbCommonInfo = dbCommonInfo;
    }

    public void setPasswordUtils(PasswordUtils passwordUtils) {
        _passwordUtils = passwordUtils;
    }

    /**
     * Set node list in current vdc.
     *
     * @param nodelist vdc host list
     */
    public void setVdcNodeList(List<String> nodelist) {
        if (_vdcHosts == null) {
            _vdcHosts = new StringMap();
        } else {
            _vdcHosts.clear();
        }
        for (int i = 0; i < nodelist.size(); i++) {
            int nodeIndex = i + 1;
            String nodeId = VDC_NODE_PREFIX + nodeIndex;
            // TODO: support both ipv4 and ipv6 later
            _vdcHosts.put(nodeId, nodelist.get(i));
        }
    }

    /**
     * Set all vdc id list.
     *
     * @param vdcList vdc id list
     */
    public void setVdcList(List<String> vdcList) {
        _vdcList = vdcList;
    }

    public List<String> getVdcList() {
        return _vdcList;
    }

    public Map<String, ColumnFamily> getCfMap() {
        return isGeoDbsvc() ? _doScanner.getGeoCfMap() : _doScanner.getCfMap();
    }

    public void setBackCompatPreYoda(Boolean backCompatPreYoda) {
        this.backCompatPreYoda = backCompatPreYoda;
    }

    /**
     * Check if it is geodbsvc
     *
     * @return
     */
    protected boolean isGeoDbsvc() {
        return _service.getName().equalsIgnoreCase(Constants.GEODBSVC_NAME);
    }

    /**
     * Initializes database. Assumes that caller is serializing this call
     * across cluster.
     *
     * @param waitForSchema - indicate we should wait from schema from other site.
     *            false to create keyspace by our own
     */
    public void scanAndSetupDb(boolean waitForSchema) {
        int retryIntervalSecs = DBINIT_RETRY_INTERVAL;
        int retryTimes = 0;
        while (true) {
            retryTimes++;
            try {
                KeyspaceDefinition kd = clientContext.getCluster().describeKeyspace(_keyspaceName);
                boolean inited = false;
                if (onStandby) {
                    inited = checkAndInitSchemaOnStandby(kd);
                } else {
                    inited = checkAndInitSchemaOnActive(kd, waitForSchema);
                }
                if (inited) {
                    return;
                }
            } catch (ConnectionException e) {
                _log.warn("Unable to verify DB keyspace, will retry in {} secs", retryIntervalSecs, e);
            } catch (InterruptedException e) {
                _log.warn("DB keyspace verification interrupted, will retry in {} secs", retryIntervalSecs, e);
            } catch (IllegalStateException e) {
                _log.warn("IllegalStateException: ", e);
                throw e;
            }

            if (retryTimes > DBINIT_RETRY_MAX) {
                throw new IllegalStateException("Unable to setup schema");
            }

            try {
                Thread.sleep(retryIntervalSecs * 1000);
            } catch (InterruptedException ex) {
                _log.warn("Thread is interrupted during wait for retry", ex);
            }
        }
    }

    private boolean checkAndInitSchemaOnActive(KeyspaceDefinition kd, boolean waitForSchema) throws InterruptedException, ConnectionException {
        _log.info("try scan and setup db ...");
        if (kd == null) {
            _log.info("keyspace not exist yet");

            if (waitForSchema) {
                _log.info("wait for schema from other site");
            }  else {
                // fresh install
                _log.info("setting current version to {} in zk for fresh install", _service.getVersion());
                setCurrentVersion(_service.getVersion());

                // this must be a new cluster - no schema is present so we create keyspace first
                Map<String, String> strategyOptions = new HashMap<String, String>(){{
                    put(_vdcShortId, Integer.toString(getReplicationFactor()));
                }};
                clientContext.setCassandraStrategyOptions(strategyOptions, true);
            }
        } else {
            _log.info("keyspace exist already");
            checkStrategyOptions();
        }

        // create CF's
        if (kd != null) {
            checkCf();
            _log.info("scan and setup db schema succeed");
            return true;
        }

        return false;
    }

    private boolean checkAndInitSchemaOnStandby(KeyspaceDefinition kd) throws ConnectionException{
        _log.info("try scan and setup db on standby site ...");
        if (kd == null) {
            _log.info("keyspace not exist yet. Wait {} seconds for schema from active site", DBINIT_RETRY_INTERVAL);
            return false;
        } else {
            _log.info("keyspace exist already");

            String currentDbSchemaVersion = _coordinator.getCurrentDbSchemaVersion();
            if (currentDbSchemaVersion == null) {
                _log.info("set current version for standby site {}", _service.getVersion());
                setCurrentVersion(_service.getVersion());
            }
            Site currentSite = drUtil.getLocalSite();
            if (currentSite.getState().equals(SiteState.STANDBY_RESUMING)) {
                // Ensure schema agreement before checking the strategy options,
                // since the strategy options from the local site might be older than the active site
                // and shouldn't be relied on any more.
                while (clientContext.ensureSchemaAgreement()) {
                    // If there are unreachable nodes, wait until there is at least
                    // one reachable node from the other site (which contains the latest db schema).
                    if (getReachableDcCount() > 1) {
                        break;
                    }
                }
            }
            checkStrategyOptions();
            return true;
        }
    }

    private int getReachableDcCount() {
        Set<String> dcNames = new HashSet<>();
        IEndpointSnitch snitch = DatabaseDescriptor.getEndpointSnitch();
        Set<InetAddress> liveNodes = Gossiper.instance.getLiveMembers();
        for (InetAddress nodeIp : liveNodes) {
            dcNames.add(snitch.getDatacenter(nodeIp));
        }
        _log.info("Number of reachable data centers: {}", dcNames.size());
        return dcNames.size();
    }

    public void rebuildDataOnStandby() {
        Site currentSite = drUtil.getLocalSite();

        if (currentSite.getState().equals(SiteState.STANDBY_ADDING) ||
            currentSite.getState().equals(SiteState.STANDBY_RESUMING)) {
            currentSite.setState(SiteState.STANDBY_SYNCING);
            _coordinator.persistServiceConfiguration(currentSite.toConfiguration());
        }

        if (currentSite.getState().equals(SiteState.STANDBY_SYNCING)) {
            dbRebuildRunnable.run();
        }
    }


    
    /**
     * Remove paused sites from db/geodb strategy options on the active site.
     *
     * @param strategyOptions
     * @return true to indicate keyspace strategy option is changed
     */
    private boolean checkStrategyOptionsForDROnActive(Map<String, String> strategyOptions) {
        boolean changed = false;

        // iterate through all the sites and exclude the paused ones
        for(Site site : drUtil.listSites()) {
            String dcId = drUtil.getCassandraDcId(site);
            if (site.getState().equals(SiteState.STANDBY_PAUSED) && strategyOptions.containsKey(dcId)) {
                _log.info("Remove dc {} from strategy options", dcId);
                strategyOptions.remove(dcId);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Put to be added or resumed standby site into the db/geodb strategy options on each new standby site
     *
     * @param strategyOptions
     * @return true to indicate keyspace strategy option is changed
     */
    private boolean checkStrategyOptionsForDROnStandby(Map<String, String> strategyOptions) {
        // no need to add new site on acitve site, since dbsvc/geodbsvc are not restarted
        String dcId = drUtil.getCassandraDcId(drUtil.getLocalSite());
        if (strategyOptions.containsKey(dcId)) {
            return false;
        }

        Site localSite = drUtil.getLocalSite();
        if (localSite.getState().equals(SiteState.STANDBY_PAUSED) ||
                localSite.getState().equals(SiteState.STANDBY_DEGRADED) ||
                localSite.getState().equals(SiteState.STANDBY_DEGRADING)) {
            // don't add back the paused site
            _log.info("local standby site has been paused/degraded and removed from strategy options. Do nothing");
            return false;
        }

        _log.info("Add {} to strategy options", dcId);
        strategyOptions.put(dcId, Integer.toString(getReplicationFactor()));
        
        // If we upgrade from pre-yoda versions, the strategy option does not contains active site.
        // we do it once during first add-standby operation on standby site
        Site activeSite = drUtil.getActiveSite();
        String activeSiteDcId = drUtil.getCassandraDcId(activeSite);
        if (!strategyOptions.containsKey(activeSiteDcId)) {
            _log.info("Add {} to strategy options", activeSiteDcId);
            strategyOptions.put(activeSiteDcId, Integer.toString(activeSite.getNodeCount()));
            if (strategyOptions.containsKey("replication_factor")) {
                strategyOptions.remove("replication_factor");
            }
        }
        return true;
    }

    /**
     * Add new VDC into the geodb strategy options
     *
     * @param strategyOptions
     * @return true to indicate keyspace strategy option is changed
     */
    private boolean checkStrategyOptionsForGeo(Map<String, String> strategyOptions) {
        if (onStandby) {
            _log.info("Only active site updates geo strategy operation. Do nothing on standby site");
            return false;
        }

        if (!isGeoDbsvc()) {
            // update local db strategy option in multivdc configuration only
            if (!drUtil.isMultivdc()) {
                return false;
            }

            if (backCompatPreYoda) {
                _log.info("Upgraded from preyoda release. Keep db strategy options unchanged.");
                return false;
            }
            // for local db, check if current vdc id is in the list
            if (!strategyOptions.containsKey(_vdcShortId)) {
                strategyOptions.clear();
                _log.info("Add {} to strategy options", _vdcShortId);
                strategyOptions.put(_vdcShortId, Integer.toString(getReplicationFactor()));
                return true;
            }
            return false;
        }
        
        _log.debug("vdcList = {}", _vdcList);
        if (_vdcList.size() == 1 && !_vdcList.contains(_vdcShortId)) {
            // the current vdc is removed
            strategyOptions.clear();
        }
        
        String dcName = _vdcShortId;
        Site currentSite = null;
        
        try {
            currentSite = drUtil.getLocalSite();
        } catch (Exception e) {
            //ignore
        }
        
        if (currentSite != null) {
            dcName = drUtil.getCassandraDcId(currentSite); 
        }
        
        
        if (strategyOptions.containsKey(dcName)) {
            return false;
        }

        _log.info("Add {} to strategy options", dcName);
        strategyOptions.put(dcName, Integer.toString(getReplicationFactor()));
        return true;
    }

    /**
     * Check keyspace strategy options for an existing keyspace and update if necessary
     */
    private void checkStrategyOptions() throws ConnectionException {
        KeyspaceDefinition kd = clientContext.getCluster().describeKeyspace(_keyspaceName);
        Map<String, String> strategyOptions = kd.getStrategyOptions();
        _log.info("Current strategyOptions={}", strategyOptions);

        boolean changed = false;
        changed |= onStandby ? checkStrategyOptionsForDROnStandby(strategyOptions) : checkStrategyOptionsForDROnActive(strategyOptions) ;
        changed |= checkStrategyOptionsForGeo(strategyOptions);

        if (changed) {
            _log.info("strategyOptions changed to {}", strategyOptions);
            clientContext.setCassandraStrategyOptions(strategyOptions, true);
        }
    }

    private Integer getIntProperty(String key, Integer defValue) {
        String strVal = _dbCommonInfo == null ? null : _dbCommonInfo.getProperty(key);
        if (strVal == null) {
            return defValue;
        } else {
            return Integer.parseInt(strVal);
        }
    }

    /**
     * Checks all required CF's against keyspace definition. Any missing
     * CF's are created on the fly.
     *
     */
    private void checkCf() throws InterruptedException, ConnectionException {
        KeyspaceDefinition kd = clientContext.getCluster().describeKeyspace(_keyspaceName);
        Cluster cluster = clientContext.getCluster();

        // Get default GC grace period for all index CFs in local DB
        Integer indexGcGrace = isGeoDbsvc() ? null : getIntProperty(DbClientImpl.DB_CASSANDRA_INDEX_GC_GRACE_PERIOD, null);

        Iterator<ColumnFamily> it = getCfMap().values().iterator();
        String latestSchemaVersion = null;
        while (it.hasNext()) {
            ColumnFamily cf = it.next();
            ColumnFamilyDefinition cfd = kd.getColumnFamily(cf.getName());
            String comparator = cf.getColumnSerializer().getComparatorType().getTypeName();
            if (comparator.equals("CompositeType")) {
                if (cf.getColumnSerializer() instanceof CompositeColumnNameSerializer) {
                    comparator = CompositeColumnNameSerializer.getComparatorName();
                } else if (cf.getColumnSerializer() instanceof IndexColumnNameSerializer) {
                    comparator = IndexColumnNameSerializer.getComparatorName();
                } else {
                    throw new IllegalArgumentException();
                }
            }

            // The CF's gc_grace_period will be set if it's an index CF
            Integer cfGcGrace = cf.getColumnSerializer() instanceof IndexColumnNameSerializer ? indexGcGrace : null;
            // If there's specific configuration particular for this CF, take it.
            cfGcGrace = getIntProperty(DbClientImpl.DB_CASSANDRA_GC_GRACE_PERIOD_PREFIX + cf.getName(), cfGcGrace);

            if (cfd == null) {
                cfd = cluster.makeColumnFamilyDefinition()
                        .setKeyspace(_keyspaceName)
                        .setName(cf.getName())
                        .setComparatorType(comparator)
                        .setKeyValidationClass(cf.getKeySerializer().getComparatorType().getTypeName());
                TimeSeriesType tsType = TypeMap.getTimeSeriesType(cf.getName());
                if (tsType != null &&
                        tsType.getCompactOptimized() &&
                        _dbCommonInfo != null &&
                        Boolean.TRUE.toString().equalsIgnoreCase(
                                _dbCommonInfo.getProperty(DbClientImpl.DB_STAT_OPTIMIZE_DISK_SPACE, "false"))) {
                    String compactionStrategy = _dbCommonInfo.getProperty(DbClientImpl.DB_CASSANDRA_OPTIMIZED_COMPACTION_STRATEGY,
                            "SizeTieredCompactionStrategy");
                    _log.info("Setting DB compaction strategy to {}", compactionStrategy);
                    int gcGrace = Integer.parseInt(_dbCommonInfo.getProperty(DbClientImpl.DB_CASSANDRA_GC_GRACE_PERIOD,
                            "864000"));  // default is 10 days
                    _log.info("Setting DB GC grace period to {}", gcGrace);
                    cfd.setCompactionStrategy(compactionStrategy)
                            .setGcGraceSeconds(gcGrace);
                } else if (cfGcGrace != null) {
                    _log.info("Setting CF:{} gc_grace_period to {}", cf.getName(), cfGcGrace.intValue());
                    cfd.setGcGraceSeconds(cfGcGrace.intValue());
                }
                latestSchemaVersion = addColumnFamily(cfd);
            } else {
                boolean modified = false;
                String existingComparator = cfd.getComparatorType();
                if (!matchComparator(existingComparator, comparator)) {
                    _log.info("Comparator mismatch: db {} / schema {}", existingComparator, comparator);
                    cfd.setComparatorType(comparator);
                    modified = true;
                }
                TimeSeriesType tsType = TypeMap.getTimeSeriesType(cf.getName());
                if (tsType != null &&
                        tsType.getCompactOptimized() &&
                        _dbCommonInfo != null) {
                    String compactionStrategy = _dbCommonInfo.getProperty(DbClientImpl.DB_CASSANDRA_OPTIMIZED_COMPACTION_STRATEGY,
                            "SizeTieredCompactionStrategy");
                    String existingStrategy = cfd.getCompactionStrategy();
                    if (existingStrategy == null || !existingStrategy.contains(compactionStrategy)) {
                        _log.info("Setting DB compaction strategy to {}", compactionStrategy);
                        cfd.setCompactionStrategy(compactionStrategy);
                        modified = true;
                    }
                    int gcGrace = Integer.parseInt(_dbCommonInfo.getProperty(DbClientImpl.DB_CASSANDRA_GC_GRACE_PERIOD,
                            "864000"));
                    if (gcGrace != cfd.getGcGraceSeconds()) {
                        _log.info("Setting DB GC grace period to {}", gcGrace);
                        cfd.setGcGraceSeconds(gcGrace);
                        modified = true;
                    }
                }
                else if (cfGcGrace != null && cfd.getGcGraceSeconds() != cfGcGrace.intValue()) {
                    _log.info("Setting CF:{} gc_grace_period to {}", cf.getName(), cfGcGrace.intValue());
                    cfd.setGcGraceSeconds(cfGcGrace.intValue());
                    modified = true;
                }
                if (modified) {
                    latestSchemaVersion = updateColumnFamily(cfd);
                }
            }
        }

        if (latestSchemaVersion != null) {
            clientContext.waitForSchemaAgreement(latestSchemaVersion);
        }
    }

    void setCurrentVersion(String currentVersion) {
        String configKind = _coordinator.getDbConfigPath(_service.getName());
        Configuration config = _coordinator.queryConfiguration(_coordinator.getSiteId(), configKind, Constants.GLOBAL_ID);
        if (config != null) {
            config.setConfig(Constants.SCHEMA_VERSION, currentVersion);
            _coordinator.persistServiceConfiguration(_coordinator.getSiteId(), config);
        } else {
            // we are expecting this to exist, because its initialized from checkGlobalConfiguration
            throw new IllegalStateException("unexpected error, db global configuration is null");
        }
    }

    void setMigrationStatus(MigrationStatus status) {
        Configuration config = _coordinator.queryConfiguration(_coordinator.getSiteId(), getDbConfigPath(), Constants.GLOBAL_ID);
        _log.debug("setMigrationStatus: target version \"{}\" status {}",
                _coordinator.getTargetDbSchemaVersion(), status.name());
        if (config == null) {
            ConfigurationImpl cfg = new ConfigurationImpl();
            cfg.setKind(getDbConfigPath());
            cfg.setId(Constants.GLOBAL_ID);
            config = cfg;
        }
        config.setConfig(Constants.MIGRATION_STATUS, status.name());
        _coordinator.persistServiceConfiguration(_coordinator.getSiteId(), config);
    }

    /**
     * Update migration checkpoint to ZK. Assume migration lock is acquired when entering this call.
     * 
     * @param checkpoint
     */
    void setMigrationCheckpoint(String checkpoint) {
        Configuration config = _coordinator.queryConfiguration(_coordinator.getSiteId(), getDbConfigPath(), Constants.GLOBAL_ID);
        _log.debug("setMigrationCheckpoint: target version \"{}\" checkpoint {}",
                _coordinator.getTargetDbSchemaVersion(), checkpoint);
        if (config == null) {
            ConfigurationImpl cfg = new ConfigurationImpl();
            cfg.setKind(getDbConfigPath());
            cfg.setId(Constants.GLOBAL_ID);
            config = cfg;
        }
        config.setConfig(DbConfigConstants.MIGRATION_CHECKPOINT, checkpoint);
        _coordinator.persistServiceConfiguration(_coordinator.getSiteId(), config);
    }

    /**
     * Get migration check point from ZK. Db migration is supposed to start from this point.
     * 
     */
    String getMigrationCheckpoint() {
        Configuration config = _coordinator.queryConfiguration(_coordinator.getSiteId(), getDbConfigPath(), Constants.GLOBAL_ID);
        _log.debug("getMigrationCheckpoint: target version \"{}\"",
                _coordinator.getTargetDbSchemaVersion());
        if (config != null) {
            String checkpoint = config.getConfig(DbConfigConstants.MIGRATION_CHECKPOINT);
            return checkpoint;
        }
        return null;
    }

    /**
     * Remove migration checkpoint from ZK. Assume migration lock is acquired when entering this call.
     * 
     */
    void removeMigrationCheckpoint() {
        Configuration config = _coordinator.queryConfiguration(_coordinator.getSiteId(), getDbConfigPath(), Constants.GLOBAL_ID);
        _log.debug("removeMigrationCheckpoint: target version \"{}\"",
                _coordinator.getTargetDbSchemaVersion());
        if (config != null) {
            config.removeConfig(DbConfigConstants.MIGRATION_CHECKPOINT);
            _coordinator.persistServiceConfiguration(_coordinator.getSiteId(), config);
        }
    }

    private String getDbConfigPath() {
        return _coordinator.getVersionedDbConfigPath(_service.getName(), _coordinator.getTargetDbSchemaVersion());
    }

    private boolean isRootTenantExist(DbClient dbClient) {
        URIQueryResultList tenants = new URIQueryResultList();
        try {
            dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getTenantOrgSubTenantConstraint(URI.create(TenantOrg.NO_PARENT)),
                    tenants);
            if (tenants.iterator().hasNext()) {
                return true;
            } else {
                _log.info("root tenant query returned no results");
                return false;
            }
        } catch (DatabaseException ex) {
            _log.error("failed querying for root tenant", ex);
            throw ex; // Throw an DatabaseException and retry
        } catch (Exception ex) {
            _log.error("unexpected error during querying for root tenant", ex);
            // throw IllegalStateExcpetion and stop
            throw new IllegalStateException("root tenant query failed");
        }
    }

    private VirtualDataCenter queryLocalVdc(DbClient dbClient) {
        // all vdc info stored in local db
        try {
            _log.debug("my vdcid: " + _vdcShortId);
            URIQueryResultList list = new URIQueryResultList();
            AlternateIdConstraint constraints = AlternateIdConstraint.Factory.getVirtualDataCenterByShortIdConstraint(_vdcShortId);
            dbClient.queryByConstraint(constraints, list);
            if (list.iterator().hasNext()) {
                URI vdcId = list.iterator().next();
                VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, vdcId);
                return vdc;
            } else {
                _log.info("vdc resource query returned no results");
                return null;
            }

        } catch (DatabaseException ex) {
            _log.error("failed querying for vdc resource", ex);
            throw ex; // Throw an DatabaseException and retry
        } catch (Exception ex) {
            _log.error("unexpected error during querying for vdc info", ex);
            // throw IllegalStateExcpetion and stop
            throw new IllegalStateException("vdc resource query failed");
        }
    }
    
    private boolean isVdcInfoExist(DbClient dbClient) {
        return queryLocalVdc(dbClient) != null;
    }

    /**
     * Check if node ip or vip is changed. VirtualDataCenter object should be updated
     * to reflect this change.
     */
    private void checkIPChanged() {
        Site site = drUtil.getLocalSite();
        Map<String, String> ipv4Addrs = site.getHostIPv4AddressMap();
        Map<String, String> ipv6Addrs = site.getHostIPv6AddressMap();

        CoordinatorClientInetAddressMap nodeMap = _coordinator.getInetAddessLookupMap();
        Map<String, DualInetAddress> controlNodes = nodeMap.getControllerNodeIPLookupMap();

        String nodeId;
        int nodeIndex = 0;
        boolean changed = false;

        // check node ip
        for (Map.Entry<String, DualInetAddress> cnode : controlNodes.entrySet()) {
            nodeIndex++;
            nodeId = VDC_NODE_PREFIX + nodeIndex;
            DualInetAddress addr = cnode.getValue();

            String inet4Addr = ipv4Addrs.get(nodeId);
            if (addr.hasInet4()) {
                String newInet4Addr = addr.getInet4();
                if (!newInet4Addr.equals(inet4Addr)) {
                    changed = true;
                    ipv4Addrs.put(nodeId, newInet4Addr);
                    _log.info(String.format("Node %s inet4 address changed from %s to %s", nodeId, inet4Addr, newInet4Addr));
                }
            } else if (inet4Addr != null) {
                changed = true;
                ipv4Addrs.remove(nodeId);
                _log.info(String.format("Node %s previous inet4 address %s removed", nodeId, inet4Addr));
            }

            String inet6Addr = ipv6Addrs.get(nodeId);
            if (addr.hasInet6()) {
                String newInet6Addr = addr.getInet6();
                if (!newInet6Addr.equals(inet6Addr)) {
                    changed = true;
                    ipv6Addrs.put(nodeId, newInet6Addr);
                    _log.info(String.format("Node %s inet6 address changed from %s to %s", nodeId, inet6Addr, newInet6Addr));
                }
            } else if (inet6Addr != null) {
                changed = true;
                ipv6Addrs.remove(nodeId);
                _log.info(String.format("Node %s previous inet6 address %s removed", nodeId, inet6Addr));
            }
        }

        // check node count
        if (_vdcHosts != null && _vdcHosts.size() != site.getNodeCount()) {
            if (_vdcHosts.size() < site.getNodeCount()) {
                for (nodeIndex = _vdcHosts.size() + 1; nodeIndex <= site.getNodeCount(); nodeIndex++) {
                    nodeId = VDC_NODE_PREFIX + nodeIndex;
                    ipv4Addrs.remove(nodeId);
                    ipv6Addrs.remove(nodeId);
                }
            }
            changed = true;
            site.setNodeCount(_vdcHosts.size());
            _log.info("Vdc host count changed from {} to {}", site.getNodeCount(), _vdcHosts.size());
        }

        // Check VIP
        if (_vdcEndpoint != null && !_vdcEndpoint.equals(site.getVip())) {
            changed = true;
            site.setVip(_vdcEndpoint);
            _log.info("Vdc vip changed to {}", _vdcEndpoint);
        }

        if (changed) {
            _coordinator.persistServiceConfiguration(site.toConfiguration());
            _log.info("vdc ip change detected, updated vdc resource ok");
        }
    }

    /**
     * Insert default root tenant
     */
    private void insertDefaultRootTenant(DbClient dbClient) {
        if (!getCfMap().containsKey(TypeMap.getDoType(TenantOrg.class).getCF().getName())) {
            _log.error("No TenantOrg CF in geodb!");
            return;
        }

        if (isRootTenantExist(dbClient)) {
            _log.info("root provider tenant exist already, skip insert");
            return;
        }

        /*
         * Following needs to move to boot strapping wizard at some point
         */
        _log.info("insert root provider tenant ...");
        TenantOrg org = new TenantOrg();
        org.setId(URIUtil.createId(TenantOrg.class));
        org.setLabel("Provider Tenant");
        org.setDescription("Root Provider Tenant");
        org.setParentTenant(new NamedURI(URI.create(TenantOrg.NO_PARENT), org.getLabel()));
        org.addRole("SID,root", "TENANT_ADMIN");
        org.setCreationTime(Calendar.getInstance());
        org.setInactive(false);
        dbClient.createObject(org);
    }

    private String getBootstrapLockName() {
        return isGeoDbsvc() ? GEODB_BOOTSTRAP_LOCK : DB_BOOTSTRAP_LOCK;
    }

    /**
     * Insert vdc info of current site
     */
    private void insertMyVdcInfo(DbClient dbClient) throws UnknownHostException {
        if (!getCfMap().containsKey(TypeMap.getDoType(VirtualDataCenter.class).getCF().getName())) {
            _log.error("Unable to find VirtualDataCenter CF in current keyspace");
            return;
        }
        VirtualDataCenter localVdc = queryLocalVdc(dbClient);
        if (localVdc != null) {
            checkIPChanged();
            return;
        }

        _log.info("insert vdc info of current site...");

        VirtualDataCenter vdc = new VirtualDataCenter();
        vdc.setId(URIUtil.createVirtualDataCenterId(_vdcShortId));
        vdc.setShortId(_vdcShortId);
        vdc.setLabel(_vdcShortId);
        vdc.setConnectionStatus(VirtualDataCenter.ConnectionStatus.ISOLATED);
        vdc.setRepStatus(VirtualDataCenter.GeoReplicationStatus.REP_NONE);
        vdc.setVersion(new Date().getTime()); // timestamp
        vdc.setApiEndpoint(_vdcEndpoint);

        vdc.setLocal(true);
        dbClient.createObject(vdc);
    }
    
    /**
     * initialize PasswordHistory CF
     * 
     * @param dbClient
     */
    private void insertPasswordHistory(DbClient dbClient) {
        String[] localUsers = { "root", "sysmonitor", "svcuser", "proxyuser" };

        for (String user : localUsers) {
            PasswordHistory passwordHistory = _passwordUtils.getPasswordHistory(user);
            if (passwordHistory == null) {
                passwordHistory = new PasswordHistory();
                passwordHistory.setId(PasswordUtils.getLocalPasswordHistoryURI(user));
                LongMap passwordHash = new LongMap();
                String encpassword = null;
                if (user.equals("proxyuser")) {
                    encpassword = _passwordUtils.getEncryptedString("ChangeMe");
                } else {
                    encpassword = _passwordUtils.getUserPassword(user);
                }
                // set the first password history entry's time to 0, to remove the impact of ChangeInterval
                // rule, if local users want to change their own password just after the installation.
                passwordHash.put(encpassword, 0L);
                passwordHistory.setUserPasswordHash(passwordHash);
                dbClient.createObject(passwordHistory);
            }
        }
    }
    
    /**
     * Init the bootstrap info, including:
     * check and setup root tenant or my vdc info, if it doesn't exist
     */
    public void checkAndSetupBootStrapInfo(DbClient dbClient) {
        // Standby site need not do the bootstrap
        if (onStandby) {
            _log.info("Skip boot strap info initialization on standby site");
            return;
        }
        
        // Only the first VDC need check root tenant
        if (_vdcList != null && _vdcList.size() > 1) {
            _log.info("Skip root tenant check for more than one vdcs. Current number of vdcs: {}", _vdcList.size());
            return;
        }
        
        int retryIntervalSecs = DBINIT_RETRY_INTERVAL;
        boolean done = false;
        boolean wait;
        while (!done) {
            wait = false;
            InterProcessLock lock = null;
            try {
                lock = _coordinator.getLock(getBootstrapLockName());
                _log.info("bootstrap info check - waiting for bootstrap lock");
                lock.acquire();

                if (isGeoDbsvc()) {
                    // insert root tenant if not exist for geodb
                    insertDefaultRootTenant(dbClient);
                } else {
                    // insert default vdc info if not exist for local db
                    insertMyVdcInfo(dbClient);
                    // insert VdcVersion if not exist for geo db, don't insert in geo db to avoid race condition.
                    insertVdcVersion(dbClient);
                    // insert local user's password history if not exist for local db
                    insertPasswordHistory(dbClient);
                }
                done = true;
            } catch (Exception e) {
                if (e instanceof IllegalStateException) {
                    throw (IllegalStateException) e;
                } else {
                    _log.warn("Exception while checking for bootstrap info, will retry in {} secs", retryIntervalSecs, e);
                    wait = true;
                }
            } finally {
                if (lock != null) {
                    try {
                        lock.release();
                    } catch (Exception e) {
                        _log.error("Fail to release lock", e);
                    }
                }
            }
            if (wait) {
                try {
                    Thread.sleep(retryIntervalSecs * 1000);
                } catch (InterruptedException ex) {
                    _log.warn("Thread is interrupted during wait for retry", ex);
                }
            }
        }
    }

    /**
     * Matches comparator names from db against code schema
     * 
     * @param dbschema
     * @param codeschema
     * @return
     */
    public boolean matchComparator(String dbschema, String codeschema) {
        // todo this should take schema versions into account
        // data object types should have version annotation + version info recorded into CF
        if (!codeschema.startsWith(COMPARATOR_PACKAGE)) {
            codeschema = COMPARATOR_PACKAGE + codeschema;
        }
        return dbschema.equals(codeschema);
    }

    /**
     * Adds CF to keyspace
     * 
     * @param def
     * @return
     */
    @SuppressWarnings("unchecked")
    public String addColumnFamily(final ColumnFamilyDefinition def) {
        AstyanaxContext<Cluster> context = clientContext.getClusterContext();
        final KeyspaceTracerFactory ks = EmptyKeyspaceTracerFactory.getInstance();
        ConnectionPool<Cassandra.Client> pool = (ConnectionPool<Cassandra.Client>) context.getConnectionPool();
        _log.info("Adding CF: {}", def.getName());
        try {
            return pool.executeWithFailover(
                    new AbstractOperationImpl<String>(
                            ks.newTracer(CassandraOperationType.ADD_COLUMN_FAMILY)) {
                        @Override
                        public String internalExecute(Cassandra.Client client, ConnectionContext context) throws Exception {
                            client.set_keyspace(_keyspaceName);
                            return client.system_add_column_family(((ThriftColumnFamilyDefinitionImpl) def)
                                    .getThriftColumnFamilyDefinition());
                        }
                    }, context.getAstyanaxConfiguration().getRetryPolicy().duplicate()).getResult();
        } catch (final OperationException e) {
            throw DatabaseException.retryables.operationFailed(e);
        } catch (final ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }

    /**
     * Updates CF
     * 
     * @param def
     * @return
     */
    @SuppressWarnings("unchecked")
    public String updateColumnFamily(final ColumnFamilyDefinition def) {
        AstyanaxContext<Cluster> context = clientContext.getClusterContext();
        final KeyspaceTracerFactory ks = EmptyKeyspaceTracerFactory.getInstance();
        ConnectionPool<Cassandra.Client> pool = (ConnectionPool<Cassandra.Client>) context.getConnectionPool();
        _log.info("Updating CF: {}", def.getName());
        try {
            return pool.executeWithFailover(
                    new AbstractOperationImpl<String>(
                            ks.newTracer(CassandraOperationType.UPDATE_COLUMN_FAMILY)) {
                        @Override
                        public String internalExecute(Cassandra.Client client, ConnectionContext context) throws Exception {
                            client.set_keyspace(_keyspaceName);
                            return client.system_update_column_family(((ThriftColumnFamilyDefinitionImpl) def)
                                    .getThriftColumnFamilyDefinition());
                        }
                    }, context.getAstyanaxConfiguration().getRetryPolicy().duplicate()).getResult();
        } catch (final OperationException e) {
            throw DatabaseException.retryables.operationFailed(e);
        } catch (final ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }

    /**
     * Drop CF
     * 
     * @param cfName column family name
     * @param context
     * @return
     */
    @SuppressWarnings("unchecked")
    public String dropColumnFamily(final String cfName, AstyanaxContext<Cluster> context) {
        final KeyspaceTracerFactory ks = EmptyKeyspaceTracerFactory.getInstance();
        ConnectionPool<Cassandra.Client> pool = (ConnectionPool<Cassandra.Client>) context.getConnectionPool();
        _log.info("Dropping CF: {}", cfName);
        try {
            return pool.executeWithFailover(
                    new AbstractOperationImpl<String>(
                            ks.newTracer(CassandraOperationType.UPDATE_COLUMN_FAMILY)) {
                        @Override
                        public String internalExecute(Cassandra.Client client, ConnectionContext context) throws Exception {
                            client.set_keyspace(_keyspaceName);
                            return client.system_drop_column_family(cfName);
                        }
                    }, context.getAstyanaxConfiguration().getRetryPolicy().duplicate()).getResult();
        } catch (final OperationException e) {
            throw DatabaseException.retryables.operationFailed(e);
        } catch (final ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }
    }

    /**
     * Get replication factor. By default, 5 is the maximum replication factor we will use.
     * If there are less than 5 nodes (where N is the number of nodes), we set replication
     * factor to N
     * 
     * @return
     */
    private int getReplicationFactor() {
        if (_coordinator == null) {
            return DEFAULT_REPLICATION_FACTOR;
        }
        int clustersize = _statusChecker.getClusterNodeCount();
        return (clustersize > MAX_REPLICATION_FACTOR) ? MAX_REPLICATION_FACTOR : clustersize;
    }

    public void insertVdcVersion(final DbClient dbClient) {

        String dbFullVersion = this._service.getVersion();
        String[] parts = StringUtils.split(dbFullVersion, DbConfigConstants.VERSION_PART_SEPERATOR);
        String version = parts[0] + "." + parts[1];
        URI vdcId = VdcUtil.getLocalVdc().getId();

        List<URI> vdcVersionIds = dbClient.queryByType(VdcVersion.class, true);
        List<VdcVersion> vdcVersions = dbClient.queryObject(VdcVersion.class, vdcVersionIds);
        _log.info("insert Vdc db version vdcId={}, dbVersion={}", vdcId, version);

        if (isVdcVersionExist(vdcVersions, vdcId, version)) {
            _log.info("Vdc db version exists already, skip insert");
            return;
        }

        VdcVersion vdcVersion = getVdcVersion(vdcVersions, vdcId);

        if (vdcVersion == null) {
            _log.info("insert new Vdc db version vdc={}, dbVersion={}", vdcId, version);
            vdcVersion = new VdcVersion();
            vdcVersion.setId(URIUtil.createId(VdcVersion.class));
            vdcVersion.setVdcId(vdcId);
            vdcVersion.setVersion(version);
            dbClient.createObject(vdcVersion);
        }

        if (!vdcVersion.getVersion().equals(version)) {
            _log.info("update Vdc db version vdc={} to dbVersion={}", vdcId, version);
            vdcVersion.setVersion(version);
            dbClient.persistObject(vdcVersion);
        }
    }

    private static VdcVersion getVdcVersion(List<VdcVersion> vdcVersions, URI vdcId) {
        if (vdcVersions == null || !vdcVersions.iterator().hasNext()) {
            return null;
        }

        for (VdcVersion vdcVersion : vdcVersions) {
            if (vdcVersion.getVdcId().equals(vdcId)) {
                return vdcVersion;
            }
        }
        return null;
    }

    private static boolean isVdcVersionExist(final List<VdcVersion> vdcVersions, final URI vdcId, final String version) {
        if (vdcVersions == null || !vdcVersions.iterator().hasNext()) {
            return false;
        }
        String origVersion = null;
        for (VdcVersion vdcVersion : vdcVersions) {
            if (vdcVersion.getVdcId().equals(vdcId)) {
                origVersion = vdcVersion.getVersion();
            }
        }
        return origVersion != null && version.equals(origVersion);
    }
    
    public boolean dropUnusedCfsIfExists() {
        AstyanaxContext<Cluster> context = clientContext.getClusterContext();
        try {
            KeyspaceDefinition kd = context.getClient().describeKeyspace(_clusterName);
            if (kd == null) {
                String errMsg = "Fatal error: Keyspace not exist when drop cf";
                _log.error(errMsg);
                throw new IllegalStateException(errMsg);
            }
            for (String cfName : DbSchemaInterceptorImpl.getIgnoreCfList()) {
                ColumnFamilyDefinition cfd = kd.getColumnFamily(cfName);
                if (cfd != null) {
            	    _log.info("drop cf {} from db", cfName);
            	    String schemaVersion = dropColumnFamily(cfName, context);
                    clientContext.waitForSchemaAgreement(schemaVersion);
                }
            }
        } catch (Exception e){
            _log.error("drop Cf error ", e);
       	    return false;
        }
        return true;
   }

    public void setDrUtil(DrUtil drUtil) {
        this.drUtil = drUtil;
        onStandby = drUtil.isStandby();
    }
}
