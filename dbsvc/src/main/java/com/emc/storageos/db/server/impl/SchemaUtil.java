/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.impl;

import java.net.URI;
import java.net.UnknownHostException;
import java.util.*;

import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.security.password.PasswordUtils;
import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Cluster;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.SSLConnectionContext;

import org.apache.commons.lang.StringUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;

import com.netflix.astyanax.*;
import com.netflix.astyanax.connectionpool.ConnectionContext;
import com.netflix.astyanax.connectionpool.ConnectionPool;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.connectionpool.exceptions.OperationException;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.astyanax.ddl.ColumnFamilyDefinition;
import com.netflix.astyanax.ddl.KeyspaceDefinition;
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.shallows.EmptyKeyspaceTracerFactory;
import com.netflix.astyanax.thrift.AbstractOperationImpl;
import com.netflix.astyanax.thrift.ThriftFamilyFactory;
import com.netflix.astyanax.thrift.ddl.ThriftColumnFamilyDefinitionImpl;

import org.apache.cassandra.thrift.Cassandra;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.client.model.MigrationStatus;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.impl.*;
import com.emc.storageos.db.common.DataObjectScanner;
import com.emc.storageos.db.common.DbConfigConstants;
import com.emc.storageos.db.common.DbSchemaInterceptorImpl;
import com.emc.storageos.db.common.DbServiceStatusChecker;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.db.client.DbClient;

/**
 * Utility class for initializing DB schema from model classes
 */
public class SchemaUtil {
    private static final Logger _log = LoggerFactory.getLogger(SchemaUtil.class);
    private static final String SOURCE_VERSION = "1.0";
    private static final String COMPARATOR_PACKAGE = "org.apache.cassandra.db.marshal.";
    private static final String REPLICATION_FACTOR = "replication_factor";
    private static final String DB_BOOTSTRAP_LOCK = "dbbootstrap";
    private static final String VDC_NODE_PREFIX = "node";
    private static final String GEODB_BOOTSTRAP_LOCK = "geodbbootstrap";

    private static final int DEFAULT_REPLICATION_FACTOR = 1;
    private static final int MAX_REPLICATION_FACTOR = 5;
    public static final long MAX_SCHEMA_WAIT_MS = 60 * 1000 * 10;
    private static final int DBINIT_RETRY_INTERVAL = 2;
    private static final int DBINIT_RETRY_MAX = 5;

    private String _clusterName = DbClientContext.LOCAL_CLUSTER_NAME;
    private String _keyspaceName = DbClientContext.LOCAL_KEYSPACE_NAME;

    private static final String KEYSPACE_SIMPLE_STRATEGY = "SimpleStrategy";
    private static final String KEYSPACE_NETWORK_TOPOLOGY_STRATEGY = "NetworkTopologyStrategy";
    private static final String DEFAULT_VDC_DB_VERSION = "2.2";

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
    private boolean standbyMode = false;

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

    public void setStandbyMode(boolean standbyMode) {
        this.standbyMode = standbyMode;
    }

    public boolean isStandbyMode() {
    	return standbyMode;
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
            AstyanaxContext<Cluster> clusterContext = null;
            _log.info("try scan and setup db ...");
            retryTimes++;
            try {
                int replicationFactor = getReplicationFactor();
                clusterContext = connectCluster();
                Cluster cluster = clusterContext.getClient();
                KeyspaceDefinition kd = cluster.describeKeyspace(_keyspaceName);
                if (kd == null) {
                    _log.info("keyspace not exist yet");

                    if (waitForSchema) {
                        _log.info("wait for schema from other site");
                    } else {
                        // fresh install
                        _log.info("setting current version to {} in zk for fresh install", _service.getVersion());
                        setCurrentVersion(_service.getVersion());

                        // this must be a new cluster - no schema is present so we create keyspace first
                        kd = cluster.makeKeyspaceDefinition();
                        setStrategyOptions(kd, replicationFactor);
                        waitForSchemaChange(cluster.addKeyspace(kd).getResult().getSchemaId(), cluster);
                    }
                } else {
                    // this is an existing cluster
                    if (!standbyMode)
                        checkStrategyOptions(kd, cluster, replicationFactor);
                }

                // create CF's
                if (kd != null && !standbyMode) {
                    checkCf(kd, clusterContext);
                    _log.info("scan and setup db schema succeed");
                    return;
                }
            } catch (ConnectionException e) {
                _log.warn("Unable to verify DB keyspace, will retry in {} secs", retryIntervalSecs, e);
            } catch (InterruptedException e) {
                _log.warn("DB keyspace verification interrupted, will retry in {} secs", retryIntervalSecs, e);
            } catch (IllegalStateException e) {
                _log.warn("IllegalStateException: ", e);
                throw e;
            } finally {
                if (clusterContext != null) {
                    clusterContext.shutdown();
                }
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

    /**
     * Set keyspace strategy class and options for a keyspace whose name specified by
     * _keyspaceName. New keyspace is created if it does exist.
     * 
     * @param keyspace
     * @param replicas
     * @return true to indicate keyspace strategy option is changed
     */
    private boolean setStrategyOptions(KeyspaceDefinition keyspace, int replicas) {
        boolean changed = false;
        keyspace.setName(_keyspaceName);

        // Get existing strategy options if the keyspace exists
        Map<String, String> stratOptions = keyspace.getStrategyOptions();
        if (isGeoDbsvc()) {
            Map<String, String> strategyOptions = keyspace.getStrategyOptions();

            _log.info("vdcList={} strategyOptions={}", _vdcList, strategyOptions);
            if (_vdcList.size() == 1) {
                // the current vdc is removed
                strategyOptions.clear();
            }

            if (strategyOptions.containsKey(_vdcShortId.toString())) {
                _log.info("The strategy options contains {}", _vdcShortId);
                return false;
            }

            _log.info("The strategy doesn't has {} so set it", _vdcShortId);
            if (_vdcShortId == null) {
                _log.info("No vdc id specified for geodbsvc");
                throw new IllegalStateException("Unexpected error. No vdc short id specified");
            }

            keyspace.setStrategyClass(KEYSPACE_NETWORK_TOPOLOGY_STRATEGY);
            stratOptions.put(_vdcShortId.toString(), Integer.toString(replicas));
            changed = true;
        } else {
        	// Todo - add standby to strategy options
            keyspace.setStrategyClass(KEYSPACE_NETWORK_TOPOLOGY_STRATEGY);
            stratOptions.put(_vdcShortId.toString(), Integer.toString(replicas));
            changed = true;
        }

        keyspace.setStrategyOptions(stratOptions);
        return changed;
    }

    /**
     * Check keyspace strategy options for an existing keyspace and update if necessary
     * 
     * @param kd
     * @param cluster
     * @param replicationFactor
     */
    private void checkStrategyOptions(KeyspaceDefinition kd, Cluster cluster, int replicationFactor)
            throws ConnectionException, InterruptedException {
        _log.info("keyspace exist already");

        String currentDbSchemaVersion = _coordinator.getCurrentDbSchemaVersion();
        if (currentDbSchemaVersion == null) {
            _log.info("missing current version in zk, assuming upgrade from {}", SOURCE_VERSION);
            setCurrentVersion(SOURCE_VERSION);
        }

        // Update keyspace strategy option
        Map<String, String> options = kd.getStrategyOptions();

        if (isGeoDbsvc()) {
            // Set current vdc to geodb strategy option if there is only one vdc
            if (!options.containsKey(_vdcShortId.toString()))
            {
                KeyspaceDefinition update = cluster.makeKeyspaceDefinition();
                update.setStrategyOptions(options);

                boolean changed = setStrategyOptions(update, getReplicationFactor());

                if (changed) {
                    waitForSchemaChange(cluster.updateKeyspace(update).getResult().getSchemaId(), cluster);
                }
            }

            return;
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
     * @param kd
     * @param clusterContext
     */
    private void checkCf(KeyspaceDefinition kd, AstyanaxContext<Cluster> clusterContext)
            throws InterruptedException, ConnectionException {
        Cluster cluster = clusterContext.getClient();

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
                latestSchemaVersion = addColumnFamily(cfd, clusterContext);
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
                    latestSchemaVersion = updateColumnFamily(cfd, clusterContext);
                }
            }
        }

        if (latestSchemaVersion != null) {
            waitForSchemaChange(latestSchemaVersion, cluster);
        }
    }

    void setCurrentVersion(String currentVersion) {
        String configKind = _coordinator.getDbConfigPath(_service.getName());
        Configuration config = _coordinator.queryConfiguration(configKind, Constants.GLOBAL_ID);
        if (config != null) {
            config.setConfig(Constants.SCHEMA_VERSION, currentVersion);
            _coordinator.persistServiceConfiguration(config);
        } else {
            // we are expecting this to exist, because its initialized from checkGlobalConfiguration
            throw new IllegalStateException("unexpected error, db global configuration is null");
        }
    }

    void setMigrationStatus(MigrationStatus status) {
        Configuration config = _coordinator.queryConfiguration(getDbConfigPath(), Constants.GLOBAL_ID);
        _log.debug("setMigrationStatus: target version \"{}\" status {}",
                _coordinator.getTargetDbSchemaVersion(), status.name());
        if (config == null) {
            ConfigurationImpl cfg = new ConfigurationImpl();
            cfg.setKind(getDbConfigPath());
            cfg.setId(Constants.GLOBAL_ID);
            config = cfg;
        }
        config.setConfig(Constants.MIGRATION_STATUS, status.name());
        _coordinator.persistServiceConfiguration(config);
    }

    /**
     * Update migration checkpoint to ZK. Assume migration lock is acquired when entering this call.
     * 
     * @param checkpoint
     */
    void setMigrationCheckpoint(String checkpoint) {
        Configuration config = _coordinator.queryConfiguration(getDbConfigPath(), Constants.GLOBAL_ID);
        _log.debug("setMigrationCheckpoint: target version \"{}\" checkpoint {}",
                _coordinator.getTargetDbSchemaVersion(), checkpoint);
        if (config == null) {
            ConfigurationImpl cfg = new ConfigurationImpl();
            cfg.setKind(getDbConfigPath());
            cfg.setId(Constants.GLOBAL_ID);
            config = cfg;
        }
        config.setConfig(DbConfigConstants.MIGRATION_CHECKPOINT, checkpoint);
        _coordinator.persistServiceConfiguration(config);
    }

    /**
     * Get migration check point from ZK. Db migration is supposed to start from this point.
     * 
     */
    String getMigrationCheckpoint() {
        Configuration config = _coordinator.queryConfiguration(getDbConfigPath(), Constants.GLOBAL_ID);
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
        Configuration config = _coordinator.queryConfiguration(getDbConfigPath(), Constants.GLOBAL_ID);
        _log.debug("removeMigrationCheckpoint: target version \"{}\"",
                _coordinator.getTargetDbSchemaVersion());
        if (config != null) {
            config.removeConfig(DbConfigConstants.MIGRATION_CHECKPOINT);
            _coordinator.persistServiceConfiguration(config);
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

    private boolean isVdcInfoExist(DbClient dbClient) {
        // all vdc info stored in local db
        try {
            _log.debug("my vdcid: " + _vdcShortId);
            URIQueryResultList list = new URIQueryResultList();
            AlternateIdConstraint constraints = AlternateIdConstraint.Factory.getVirtualDataCenterByShortIdConstraint(_vdcShortId);
            dbClient.queryByConstraint(constraints, list);
            if (list.iterator().hasNext()) {
                URI vdcId = list.iterator().next();
                VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, vdcId);
                if (vdc.getLocal()) {
                    checkIPChanged(vdc, dbClient);
                } else {
                    _log.warn("vdc {} is not local vdc. ignore ip check", vdc.getId().toString());
                }
                return true;
            } else {
                _log.info("vdc resource query returned no results");
                return false;
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

    /**
     * Check if node ip or vip is changed. VirtualDataCenter object should be updated
     * to reflect this change.
     * 
     * @param vdc
     * @param dbClient
     */
    private void checkIPChanged(VirtualDataCenter vdc, DbClient dbClient) {
        StringMap ipv4Addrs = vdc.getHostIPv4AddressesMap();
        StringMap ipv6Addrs = vdc.getHostIPv6AddressesMap();

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
        if (_vdcHosts != null && _vdcHosts.size() != vdc.getHostCount()) {
            if (_vdcHosts.size() < vdc.getHostCount()) {
                for (nodeIndex = _vdcHosts.size() + 1; nodeIndex <= vdc.getHostCount(); nodeIndex++) {
                    nodeId = VDC_NODE_PREFIX + nodeIndex;
                    ipv4Addrs.remove(nodeId);
                    ipv6Addrs.remove(nodeId);
                }
            }
            changed = true;
            vdc.setHostCount(_vdcHosts.size());
            _log.info("Vdc host count changed from {} to {}", vdc.getHostCount(), _vdcHosts.size());
        }

        // Check VIP
        if (_vdcEndpoint != null && !_vdcEndpoint.equals(vdc.getApiEndpoint())) {
            changed = true;
            vdc.setApiEndpoint(_vdcEndpoint);
            _log.info("Vdc vip changed to {}", _vdcEndpoint);
        }

        if (changed) {
            vdc.setVersion(new Date().getTime()); // timestamp
            dbClient.updateAndReindexObject(vdc);
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

        if (isVdcInfoExist(dbClient)) {
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
        vdc.setHostCount(_vdcHosts.size());
        vdc.setApiEndpoint(_vdcEndpoint);

        CoordinatorClientInetAddressMap nodeMap = _coordinator.getInetAddessLookupMap();
        Map<String, DualInetAddress> controlNodes = nodeMap.getControllerNodeIPLookupMap();
        StringMap ipv4Addresses = new StringMap();
        StringMap ipv6Addresses = new StringMap();

        String nodeId;
        int nodeIndex = 0;
        for (Map.Entry<String, DualInetAddress> cnode : controlNodes.entrySet()) {
            nodeIndex++;
            nodeId = VDC_NODE_PREFIX + nodeIndex;
            DualInetAddress addr = cnode.getValue();
            if (addr.hasInet4()) {
                ipv4Addresses.put(nodeId, addr.getInet4());
            }
            if (addr.hasInet6()) {
                ipv6Addresses.put(nodeId, addr.getInet6());
            }
        }

        vdc.setHostIPv4AddressesMap(ipv4Addresses);
        vdc.setHostIPv6AddressesMap(ipv6Addresses);

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
        // Only the first VDC need check root tenant
        if (isGeoDbsvc()) {
            if (_vdcList != null && _vdcList.size() > 1) {
                _log.info("Skip root tenant check for more than one vdcs. Current number of vdcs: {}", _vdcList.size());
                return;
            }
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
     * @param context
     * @return
     */
    @SuppressWarnings("unchecked")
    public String addColumnFamily(final ColumnFamilyDefinition def, AstyanaxContext<Cluster> context) {
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
     * @param context
     * @return
     */
    @SuppressWarnings("unchecked")
    public String updateColumnFamily(final ColumnFamilyDefinition def,
            AstyanaxContext<Cluster> context) {
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
    public String dropColumnFamily(final String cfName,
            AstyanaxContext<Cluster> context) {
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
     * Waits for schema change to propagate through cluster
     * 
     * @param schemaVersion version we are waiting for
     * @param cluster
     * @throws InterruptedException
     */
    private void waitForSchemaChange(String schemaVersion, Cluster cluster) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < MAX_SCHEMA_WAIT_MS) {
            Map<String, List<String>> versions;
            try {
                versions = cluster.describeSchemaVersions();
            } catch (final ConnectionException e) {
                throw DatabaseException.retryables.connectionFailed(e);
            }

            _log.info("schema version to sync to: {}", schemaVersion);
            _log.info("schema versions found: {}", versions);

            if (versions.size() == 1 && versions.containsKey(schemaVersion)) {
                _log.info("schema version sync to: {} done", schemaVersion);
                return;
            }

            _log.info("waiting for schema change ...");
            Thread.sleep(1000);
        }
        _log.warn("Unable to sync schema version {}", schemaVersion);
    }

    public void removeVdcFromStrageOption(String shortVdcId) throws Exception {
        AstyanaxContext<Cluster> clusterContext = null;
        clusterContext = connectCluster();
        Cluster cluster = clusterContext.getClient();
        KeyspaceDefinition kd = cluster.describeKeyspace(_keyspaceName);
        Map<String, String> strategyOptions = kd.getStrategyOptions();
        strategyOptions.remove(shortVdcId);
        Map<String, Object> options = new HashMap(strategyOptions.size());
        Set<Map.Entry<String, String>> strategyOpts = strategyOptions.entrySet();
        for (Map.Entry<String, String> opt : strategyOpts) {
            options.put(opt.getKey(), opt.getValue());
        }

        waitForSchemaChange(cluster.updateKeyspace(options).getResult().getSchemaId(), cluster);
    }

    public void waitForStrategyOptionChange(String shortVdcId, boolean isDisconnect) throws InterruptedException {
        AstyanaxContext<Cluster> clusterContext = connectCluster();

        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < MAX_SCHEMA_WAIT_MS) {
            Map<String, String> options;
            try {
                Cluster cluster = clusterContext.getClient();
                KeyspaceDefinition ksd = cluster.describeKeyspace(DbClientContext.GEO_KEYSPACE_NAME);
                options = ksd.getStrategyOptions();

                if (isDisconnect) {
                    if (!options.containsKey(shortVdcId)) {
                        _log.info("The strategy option has been changed");
                        return;
                    }
                } else {
                    // this is reconnect operation
                    if (options.containsKey(shortVdcId)) {
                        _log.info("The strategy option has been changed");
                        return;
                    }
                }
            } catch (final ConnectionException e) {
                throw DatabaseException.retryables.connectionFailed(e);
            } finally {
                if (clusterContext != null) {
                    clusterContext.shutdown();
                }
            }

            _log.info("waiting for strategy option change: {}", options);
            Thread.sleep(1000);
        }
    }

    /**
     * Connects to local dbsvc
     * 
     * @return
     */
    private AstyanaxContext<Cluster> connectCluster() {
        String host = _service.getEndpoint().getHost();
        _log.info("host: " + host);
        CoordinatorClientInetAddressMap nodeMap = _coordinator.getInetAddessLookupMap();
        _log.info("nodeMap: " + nodeMap);
        URI uri = nodeMap.expandURI(_service.getEndpoint());
        _log.info("uri: " + uri);

        ConnectionPoolConfigurationImpl cfg = new ConnectionPoolConfigurationImpl(_clusterName)
                .setMaxConnsPerHost(1)
                .setSeeds(String.format("%1$s:%2$d", uri.getHost(),
                        uri.getPort()));

        if (clientContext.isClientToNodeEncrypted()) {
            SSLConnectionContext sslContext = clientContext.getSSLConnectionContext();
            cfg.setSSLConnectionContext(sslContext);
        }

        AstyanaxContext<Cluster> clusterContext = new AstyanaxContext.Builder()
                .forCluster(_clusterName)
                .forKeyspace(_keyspaceName)
                .withAstyanaxConfiguration(new AstyanaxConfigurationImpl()
                        .setRetryPolicy(new QueryRetryPolicy(10, 1000)))
                .withConnectionPoolConfiguration(cfg)
                .buildCluster(ThriftFamilyFactory.getInstance());
        clusterContext.start();
        return clusterContext;
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

        VdcVersion vdcCersion = getVdcVersion(vdcVersions, vdcId);

        if (vdcCersion == null) {
            _log.info("insert new Vdc db version vdc={}, dbVersion={}", vdcId, version);
            vdcCersion = new VdcVersion();
            vdcCersion.setId(URIUtil.createId(VdcVersion.class));
            vdcCersion.setVdcId(vdcId);
            vdcCersion.setVersion(version);
            ;
            dbClient.createObject(vdcCersion);
        }

        if (!vdcCersion.getVersion().equals(version)) {
            _log.info("update Vdc db version vdc={} to dbVersion={}", vdcId, version);
            vdcCersion.setVersion(version);
            dbClient.persistObject(vdcCersion);
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
        AstyanaxContext<Cluster> context =  connectCluster();
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
                    waitForSchemaChange(schemaVersion, context.getClient());
                }
            }
        } catch (Exception e){
            _log.error("drop Cf error ", e);
       	    return false;
        }
        return true;
   }
}