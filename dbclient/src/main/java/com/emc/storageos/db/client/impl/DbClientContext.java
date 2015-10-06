/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public static final long MAX_SCHEMA_WAIT_MS = 60 * 1000 * 10;
    public static final String LOCAL_HOST = "127.0.0.1";
    public static final int DB_THRIFT_PORT = 9160;
    public static final int GEODB_THRIFT_PORT = 9260;
    public static final String KEYSPACE_NETWORK_TOPOLOGY_STRATEGY = "NetworkTopologyStrategy";

    public static final String LOCAL_CLUSTER_NAME = "StorageOS";
    public static final String LOCAL_KEYSPACE_NAME = "StorageOS";
    public static final String GEO_CLUSTER_NAME = "GeoStorageOS";
    public static final String GEO_KEYSPACE_NAME = "GeoStorageOS";

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

    public void init(HostSupplierImpl hostSupplier) {
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
        initDone = true;
    }

    private void initClusterContext() {
        int port = getKeyspaceName().equals(LOCAL_KEYSPACE_NAME) ? DB_THRIFT_PORT : GEODB_THRIFT_PORT;

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

    public SSLConnectionContext getSSLConnectionContext() {
        List<String> cipherSuites = new ArrayList<>(1);
        cipherSuites.add(cipherSuite);

        return new SSLConnectionContext(trustStoreFile, trustStorePassword, SSLConnectionContext.DEFAULT_SSL_PROTOCOL, cipherSuites);
    }

    public synchronized void stop() {
        if (keyspaceContext == null || clusterContext == null) {
            throw new IllegalStateException();
        }

        keyspaceContext.shutdown();
        keyspaceContext = null;

        clusterContext.shutdown();
        clusterContext = null;
    }

    public void setCassandraStrategyOptions(Map<String, String> strategyOptions, boolean wait) throws Exception {
        /*int port = getKeyspaceName().equals(LOCAL_KEYSPACE_NAME) ? DB_THRIFT_PORT : GEODB_THRIFT_PORT;

        ConnectionPoolConfigurationImpl cfg = new ConnectionPoolConfigurationImpl(clusterName)
                .setMaxConnsPerHost(1)
                .setSeeds(String.format("%1$s:%2$d", LOCAL_HOST, port));

        if (isClientToNodeEncrypted()) {
            SSLConnectionContext sslContext = getSSLConnectionContext();
            cfg.setSSLConnectionContext(sslContext);
        }*/
        KeyspaceDefinition kd = keyspace.describeKeyspace();
        String schemaVersion;

        if (kd != null) {
            kd.setStrategyOptions(strategyOptions);

            schemaVersion = cluster.updateKeyspace(kd).getResult().getSchemaId();
        } else {
            kd = cluster.makeKeyspaceDefinition();
            kd.setName(getKeyspaceName());
            kd.setStrategyClass(KEYSPACE_NETWORK_TOPOLOGY_STRATEGY);
            kd.setStrategyOptions(strategyOptions);

            schemaVersion = cluster.addKeyspace(kd).getResult().getSchemaId();
        }

        if (wait) {
            waitForSchemaChange(schemaVersion, cluster);
        }
    }

    /**
     * Waits for schema change to propagate through cluster
     *
     * @param schemaVersion version we are waiting for
     * @param cluster
     * @throws InterruptedException
     */
    public void waitForSchemaChange(String schemaVersion, Cluster cluster) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < DbClientContext.MAX_SCHEMA_WAIT_MS) {
            Map<String, List<String>> versions;
            try {
                versions = cluster.describeSchemaVersions();
            } catch (final ConnectionException e) {
                throw DatabaseException.retryables.connectionFailed(e);
            }

            log.info("schema version to sync to: {}", schemaVersion);
            log.info("schema versions found: {}", versions);

            if (versions.size() == 1 && versions.containsKey(schemaVersion)) {
                log.info("schema version sync to: {} done", schemaVersion);
                return;
            }

            log.info("waiting for schema change ...");
            Thread.sleep(1000);
        }
        log.warn("Unable to sync schema version {}", schemaVersion);
    }
}
