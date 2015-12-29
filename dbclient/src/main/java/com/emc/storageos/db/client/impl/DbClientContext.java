/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Cluster;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.ddl.KeyspaceDefinition;
import com.netflix.astyanax.connectionpool.SSLConnectionContext;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.connectionpool.Host;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolType;
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl;
import com.netflix.astyanax.model.ConsistencyLevel;
import com.netflix.astyanax.partitioner.Murmur3Partitioner;
import com.netflix.astyanax.partitioner.Partitioner;
import com.netflix.astyanax.retry.RetryPolicy;
import com.netflix.astyanax.thrift.ThriftFamilyFactory;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.locator.IEndpointSnitch;
import org.apache.cassandra.service.StorageProxy;
import org.apache.cassandra.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.db.exceptions.DatabaseException;

public class DbClientContext {

    private static final Logger log = LoggerFactory.getLogger(DbClientContext.class);

    private static final int DEFAULT_MAX_CONNECTIONS = 64;
    private static final int DEFAULT_MAX_CONNECTIONS_PER_HOST = 14;
    private static final int DEFAULT_SVCLIST_POLL_INTERVAL_SEC = 5;
    private static final int DEFAULT_CONN_TIMEOUT = 1000 * 5;
    private static final int DEFAULT_MAX_BLOCKED_THREADS = 500;
    private static final String DEFAULT_CN_POOL_NANE = "DbClientPool";
    private static final long DEFAULT_CONNECTION_POOL_MONITOR_INTERVAL = 1000;
    private static final int MAX_QUERY_RETRY = 5;
    private static final int QUERY_RETRY_SLEEP_SECONDS = 1000;
    private static final String LOCAL_HOST = "localhost";
    private static final int DB_THRIFT_PORT = 9160;
    private static final int GEODB_THRIFT_PORT = 9260;
    private static final String KEYSPACE_NETWORK_TOPOLOGY_STRATEGY = "NetworkTopologyStrategy";
    private static final int DEFAULT_CONSISTENCY_LEVEL_CHECK_SEC = 30;
    // a normal node removal should succeed in 30s.
    private static final int REMOVE_NODE_TIMEOUT_MILLIS = 1 * 60 * 1000; // 1 min

    public static final String LOCAL_CLUSTER_NAME = "StorageOS";
    public static final String LOCAL_KEYSPACE_NAME = "StorageOS";
    public static final String GEO_CLUSTER_NAME = "GeoStorageOS";
    public static final String GEO_KEYSPACE_NAME = "GeoStorageOS";
    public static final long MAX_SCHEMA_WAIT_MS = 60 * 1000 * 10; // 10 minutes
    public static final int SCHEMA_RETRY_SLEEP_MILLIS = 10 * 1000; // 10 seconds

    private int maxConnections = DEFAULT_MAX_CONNECTIONS;
    private int maxConnectionsPerHost = DEFAULT_MAX_CONNECTIONS_PER_HOST;
    private int svcListPoolIntervalSec = DEFAULT_SVCLIST_POLL_INTERVAL_SEC;
    private long monitorIntervalSecs = DEFAULT_CONNECTION_POOL_MONITOR_INTERVAL;
    private RetryPolicy retryPolicy = new QueryRetryPolicy(MAX_QUERY_RETRY, QUERY_RETRY_SLEEP_SECONDS);
    private String keyspaceName = LOCAL_KEYSPACE_NAME;
    private String clusterName = LOCAL_CLUSTER_NAME;

    private AstyanaxContext<Keyspace> keyspaceContext;
    private Keyspace keyspace;
    private AstyanaxContext<Cluster> clusterContext;
    private Cluster cluster;

    private boolean initDone = false;
    private String cipherSuite;
    private String keyStoreFile;
    private String trustStoreFile;
    private String trustStorePassword;
    private boolean isClientToNodeEncrypted;
    private ScheduledExecutorService exe = Executors.newScheduledThreadPool(1);
    private ExecutorService removeNodeExecutor = Executors.newCachedThreadPool();

    public void setCipherSuite(String cipherSuite) {
        this.cipherSuite = cipherSuite;
    }

    public String getKeyspaceName() {
        return keyspaceName;
    }

    public void setKeyspaceName(String keyspaceName) {
        this.keyspaceName = keyspaceName;
    }

    public void setClientToNodeEncrypted(boolean isClientToNodeEncrypted) {
        this.isClientToNodeEncrypted = isClientToNodeEncrypted;
    }

    public boolean isClientToNodeEncrypted() {
        return isClientToNodeEncrypted;
    }

    public Keyspace getKeyspace() {
        if (keyspaceContext == null) {
            throw new IllegalStateException();
        }
        return keyspace;
    }

    public AstyanaxContext<Cluster> getClusterContext() {
        if (clusterContext == null) {
            initClusterContext();
        }
        return clusterContext;
    }

    public Cluster getCluster() {
        if (clusterContext == null) {
            initClusterContext();
        }
        return cluster;
    }

    public void setHosts(Collection<Host> hosts) {
        if (keyspaceContext == null) {
            throw new IllegalStateException();
        }
        keyspaceContext.getConnectionPool().setHosts(hosts);
    }

    public int getPort() {
        return keyspaceContext.getConnectionPoolConfiguration().getPort();
    }

    /**
     * Cluster name
     * 
     * @param clusterName
     */
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public void setMaxConnectionsPerHost(int maxConnectionsPerHost) {
        this.maxConnectionsPerHost = maxConnectionsPerHost;
    }

    public void setSvcListPoolIntervalSec(int svcListPoolIntervalSec) {
        this.svcListPoolIntervalSec = svcListPoolIntervalSec;
    }

    public void setRetryPolicy(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    /**
     * Sets the monitoring interval for client connection pool stats
     * 
     * @param monitorIntervalSecs
     */
    public void setMonitorIntervalSecs(long monitorIntervalSecs) {
        this.monitorIntervalSecs = monitorIntervalSecs;
    }
    
    public long getMonitorIntervalSecs() {
        return monitorIntervalSecs;
    }
    
    public boolean isInitDone() {
        return initDone;
    }

    public void setTrustStoreFile(String trustStoreFile) {
        this.trustStoreFile = trustStoreFile;
    }

    public String getTrustStoreFile() {
        return trustStoreFile;
    }

    public String getKeyStoreFile() {
        return keyStoreFile;
    }

    public void setKeyStoreFile(String keyStoreFile) {
        this.keyStoreFile = keyStoreFile;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public void init(final HostSupplierImpl hostSupplier) {
        String svcName = hostSupplier.getDbSvcName();
        log.info("Initializing hosts for {}", svcName);
        List<Host> hosts = hostSupplier.get();
        if ((hosts != null) && (hosts.isEmpty())) {
            throw new IllegalStateException(String.format("DbClientContext.init() : host list in hostsupplier for %s is empty", svcName));
        } else {
            int hostCount = hosts == null ? 0 : hosts.size();
            log.info(String.format("number of hosts in the hostsupplier for %s is %d", svcName, hostCount));
        }
        Partitioner murmur3partitioner = Murmur3Partitioner.get();
        Map<String, Partitioner> partitioners = new HashMap<>();
        partitioners.put("org.apache.cassandra.dht.Murmur3Partitioner.class.getCanonicalName()",
                murmur3partitioner);

        ConsistencyLevel readCL = ConsistencyLevel.CL_LOCAL_QUORUM;
        ConsistencyLevel writeCL = ConsistencyLevel.CL_EACH_QUORUM;

        ConnectionPoolConfigurationImpl cfg = new ConnectionPoolConfigurationImpl(DEFAULT_CN_POOL_NANE).setMaxConns(maxConnections)
                .setMaxConnsPerHost(maxConnectionsPerHost).setConnectTimeout(DEFAULT_CONN_TIMEOUT)
                .setMaxBlockedThreadsPerHost(DEFAULT_MAX_BLOCKED_THREADS).setPartitioner(murmur3partitioner);

        log.info("The client to node is encrypted={}", isClientToNodeEncrypted);
        if (isClientToNodeEncrypted) {
            SSLConnectionContext sslContext = getSSLConnectionContext();
            cfg.setSSLConnectionContext(sslContext);
        }

        // TODO revisit it to see if we need set different retry policy, timeout, discovery delay etc for geodb
        keyspaceContext = new AstyanaxContext.Builder().withHostSupplier(hostSupplier)
                .forCluster(clusterName)
                .forKeyspace(keyspaceName)
                .withAstyanaxConfiguration(
                        new AstyanaxConfigurationImpl().setConnectionPoolType(ConnectionPoolType.ROUND_ROBIN)
                                .setDiscoveryDelayInSeconds(svcListPoolIntervalSec)
                                .setDefaultReadConsistencyLevel(readCL)
                                .setDefaultWriteConsistencyLevel(writeCL)
                                .setTargetCassandraVersion("2.0").setPartitioners(partitioners)
                                .setRetryPolicy(retryPolicy))
                .withConnectionPoolConfiguration(cfg)
                .withConnectionPoolMonitor(new CustomConnectionPoolMonitor(monitorIntervalSecs))
                .buildKeyspace(ThriftFamilyFactory.getInstance());
        keyspaceContext.start();
        keyspace = keyspaceContext.getClient();

        // Check and reset default write consistency level
        final DrUtil drUtil = new DrUtil(hostSupplier.getCoordinatorClient());
        if (drUtil.isActiveSite()) {
            log.info("Schedule db consistency level monitor on DR acitve site");
            exe.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    try {
                        checkAndResetConsistencyLevel(drUtil, hostSupplier.getDbSvcName(), hostSupplier.getDbClientVersion());
                    } catch (Exception ex) {
                        log.warn("Encounter Unexpected exception during check consistency level. Retry in next run", ex);
                    }
                }
            }, 60, DEFAULT_CONSISTENCY_LEVEL_CHECK_SEC, TimeUnit.SECONDS);
        }
        
        initDone = true;
    }

    /**
     * Initialize the cluster context and cluster instances.
     * This has to be separated from init() because dbsvc need this to start
     * while init() depends on dbclient which in turn depends on dbsvc.
     */
    private void initClusterContext() {
        int port = getThriftPort();

        ConnectionPoolConfigurationImpl cfg = new ConnectionPoolConfigurationImpl(clusterName)
                .setMaxConnsPerHost(1)
                .setSeeds(String.format("%1$s:%2$d", LOCAL_HOST, port));

        if (isClientToNodeEncrypted()) {
            SSLConnectionContext sslContext = getSSLConnectionContext();
            cfg.setSSLConnectionContext(sslContext);
        }

        clusterContext = new AstyanaxContext.Builder()
                .forCluster(clusterName)
                .forKeyspace(getKeyspaceName())
                .withAstyanaxConfiguration(new AstyanaxConfigurationImpl()
                        .setRetryPolicy(new QueryRetryPolicy(10, 1000)))
                .withConnectionPoolConfiguration(cfg)
                .buildCluster(ThriftFamilyFactory.getInstance());
        clusterContext.start();
        cluster = clusterContext.getClient();
    }

    /**
     * Check if it is geodbsvc
     *
     * @return
     */
    public boolean isGeoDbsvc() {
        return getKeyspaceName().equals(GEO_KEYSPACE_NAME);
    }

    protected int getThriftPort() {
        int port = isGeoDbsvc() ? GEODB_THRIFT_PORT : DB_THRIFT_PORT;
        return port;
    }

    public SSLConnectionContext getSSLConnectionContext() {
        List<String> cipherSuites = new ArrayList<>(1);
        cipherSuites.add(cipherSuite);

        return new SSLConnectionContext(trustStoreFile, trustStorePassword, SSLConnectionContext.DEFAULT_SSL_PROTOCOL, cipherSuites);
    }

    public synchronized void stop() {
        if (keyspaceContext == null) {
            throw new IllegalStateException();
        }

        keyspaceContext.shutdown();
        keyspaceContext = null;

        if (clusterContext != null) {
            clusterContext.shutdown();
            clusterContext = null;
        }

        exe.shutdownNow();
        removeNodeExecutor.shutdownNow();
    }

    /**
     * Update the strategy options for db or geodb service, depending on the content of this context instance.
     *
     * @param strategyOptions new strategy options to be updated
     * @param wait whether need to wait until schema agreement is reached.
     * @throws Exception
     */
    public void setCassandraStrategyOptions(Map<String, String> strategyOptions, boolean wait) {
        try {
            Cluster cluster = getCluster();
            KeyspaceDefinition kd = cluster.describeKeyspace(keyspaceName);
    
            KeyspaceDefinition update = cluster.makeKeyspaceDefinition();
            update.setName(getKeyspaceName());
            update.setStrategyClass(KEYSPACE_NETWORK_TOPOLOGY_STRATEGY);
            update.setStrategyOptions(strategyOptions);

            // ensure a schema agreement before updating the strategy options
            // or else it's destined to fail due to SchemaDisagreementException
            ensureSchemaAgreement();

            String schemaVersion;
            if (kd != null) {
                schemaVersion = cluster.updateKeyspace(update).getResult().getSchemaId();
            } else {
                schemaVersion = cluster.addKeyspace(update).getResult().getSchemaId();
            }
    
            if (wait) {
                waitForSchemaAgreement(schemaVersion);
            }
        } catch (ConnectionException ex) {
            log.error("Fail to update strategy option", ex);
            throw DatabaseException.fatals.failedToChangeStrategyOption(ex.getMessage());
        }
    }

    /**
     * Remove a specific dc from strategy options, and wait till the new schema reaches all sites.
     * If the dc doesn't exist in the current strategy options, nothing changes.
     *
     * @param dcId the dc to be removed
     * @throws Exception
     */
    public void removeDcFromStrategyOptions(String dcId)  {
        Map<String, String> strategyOptions;
        try {
            strategyOptions = getKeyspace().describeKeyspace().getStrategyOptions();
        } catch (ConnectionException ex) {
            log.error("Unexpected errors to describe keyspace", ex);
            throw DatabaseException.fatals.failedToChangeStrategyOption(ex.getMessage());
        }
        if (strategyOptions.containsKey(dcId)) {
            log.info("Remove dc {} from strategy options", dcId);
            strategyOptions.remove(dcId);

            setCassandraStrategyOptions(strategyOptions, true);
        }
    }

    /**
     * Forcibly reach a schema agreement
     * If a schema agreement cannot be reached ONLY because there are unreachable nodes,
     * remove these unreachable nodes and try again.
     *
     * This should be fine since all the removed nodes will add themselves back to gossip
     * should they ever come back again.
     */
    public void ensureSchemaAgreement() {
        long start = System.currentTimeMillis();
        Map<String, List<String>> schemas = null;
        while (System.currentTimeMillis() - start < DbClientContext.MAX_SCHEMA_WAIT_MS) {
            try {
                log.info("sleep for {} seconds before checking schema versions.",
                        DbClientContext.SCHEMA_RETRY_SLEEP_MILLIS / 1000);
                Thread.sleep(DbClientContext.SCHEMA_RETRY_SLEEP_MILLIS);
            } catch (InterruptedException ex) {
                log.warn("Interrupted during sleep");
            }

            schemas = getSchemaVersions();
            if (schemas.size() > 2) {
                // there are more than two schema versions besides UNREACHABLE, keep waiting.
                continue;
            }
            if (schemas.size() == 1 && !schemas.containsKey(StorageProxy.UNREACHABLE)) {
                // schema agreement reached and we are all set
                return;
            }
            // schemas.size() == 2, try removing the unreachable nodes from all sites and check again.
            if (schemas.containsKey(StorageProxy.UNREACHABLE)) {
                for (String nodeIp : schemas.get(StorageProxy.UNREACHABLE)) {
                    try {
                        removeCassandraNode(InetAddress.getByName(nodeIp));
                    } catch (UnknownHostException e) {
                        log.error("Invalid node IP: {}", nodeIp);
                        throw new IllegalStateException(e);
                    }
                }
            }
        }
        log.error("Unable to converge schema versions {}", schemas);
        throw new IllegalStateException("Unable to converge schema versions");
    }

    private void removeCassandraNode(InetAddress nodeIp) {
        Map<String, String> hostIdMap = StorageService.instance.getHostIdMap();
        String guid = hostIdMap.get(nodeIp.getHostAddress());
        log.info("Removing Cassandra node {} on vipr node {}", guid, nodeIp);
        Gossiper.instance.convict(nodeIp, 0);
        ensureRemoveNode(guid);
    }

    public void removeDataCenter(String dcName) {
        log.info("Remove Cassandra data center {}", dcName);
        List<InetAddress> allNodes = new ArrayList<>();
        Set<InetAddress> liveNodes = Gossiper.instance.getLiveMembers();
        allNodes.addAll(liveNodes);
        Set<InetAddress> unreachableNodes = Gossiper.instance.getUnreachableMembers();
        allNodes.addAll(unreachableNodes);
        for (InetAddress nodeIp : allNodes) {
            IEndpointSnitch snitch = DatabaseDescriptor.getEndpointSnitch();
            String dc = snitch.getDatacenter(nodeIp);
            log.info("node {} belongs to data center {} ", nodeIp, dc);
            if (dc.equals(dcName)) {
                removeCassandraNode(nodeIp);
            }
        }
    }

    /**
     * A safer method to remove Cassandra node. Calls forceRemoveCompletion after REMOVE_NODE_TIMEOUT_MILLIS
     * This will help to prevent node removal from hanging due to CASSANDRA-6542.
     *
     * @param guid
     */
    public void ensureRemoveNode(final String guid) {
        Future<?> future = removeNodeExecutor.submit(new Runnable() {
            public void run() {
                StorageService.instance.removeNode(guid);
            }
        });
        try {
            future.get(REMOVE_NODE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("removenode timeout, calling forceRemoveCompletion()");
            StorageService.instance.forceRemoveCompletion();
        } catch (InterruptedException | ExecutionException e) {
            log.warn("Exception calling removenode", e);
        }
    }

    /**
     * Waits for schema change to propagate through cluster
     *
     * @param targetSchemaVersion version we are waiting for
     * @throws InterruptedException
     */
    public void waitForSchemaAgreement(String targetSchemaVersion) {
        long start = System.currentTimeMillis();
        Map<String, List<String>> versions = null;
        while (System.currentTimeMillis() - start < MAX_SCHEMA_WAIT_MS) {
            log.info("schema version to sync to: {}", targetSchemaVersion);
            versions = getSchemaVersions();

            if (versions.size() == 1) {
                if (!versions.containsKey(targetSchemaVersion)) {
                    log.warn("Unable to converge to target version. Schema versions:{}, target version:{}",
                            versions, targetSchemaVersion);
                    return;
                }
                log.info("schema versions converged to target version {}", targetSchemaVersion);
                return;
            }

            log.info("waiting for schema change ...");
            try {
                Thread.sleep(SCHEMA_RETRY_SLEEP_MILLIS);
            } catch (InterruptedException ex) {}
        }
        log.warn("Unable to converge schema versions: {}", versions);
    }

    /**
     * Get Cassandra schema versions -> nodes mapping.
     *
     * @return
     */
    public Map<String, List<String>> getSchemaVersions() {
        Map<String, List<String>> versions;
        try {
            versions = getCluster().describeSchemaVersions();
        } catch (final ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }

        log.info("schema versions found: {}", versions);
        return versions;
    }

    private void checkAndResetConsistencyLevel(DrUtil drUtil, String svcName, String dbVersion) {
        ConsistencyLevel currentConsistencyLevel = getKeyspace().getConfig().getDefaultWriteConsistencyLevel();
        if (currentConsistencyLevel.equals(ConsistencyLevel.CL_EACH_QUORUM)) {
            log.debug("Write consistency level is EACH_QUORUM. No need adjust");
            return;
        }
        
        log.info("Db consistency level for {} is downgraded as LOCAL_QUORUM. Check if we need reset it back", svcName);
        for(Site site : drUtil.listStandbySites()) {
            if (site.getState().equals(SiteState.STANDBY_PAUSED)) {
                continue; // ignore a standby site which is paused by customer explicitly
            }
            String siteUuid = site.getUuid();
            int count = drUtil.getNumberOfLiveServices(siteUuid, svcName, dbVersion);
            if (count <= site.getNodeCount() / 2) {
                log.info("Service {} of quorum nodes on site {} is down. Still keep write consistency level to LOCAL_QUORUM", svcName, siteUuid);
                return;
            }      
        }
        log.info("Service {} of quorum nodes on all standby sites are up. Reset default write consistency level back to EACH_QUORUM", svcName);
        AstyanaxConfigurationImpl config = (AstyanaxConfigurationImpl)keyspaceContext.getAstyanaxConfiguration();
        config.setDefaultWriteConsistencyLevel(ConsistencyLevel.CL_EACH_QUORUM);
    }
    
}
