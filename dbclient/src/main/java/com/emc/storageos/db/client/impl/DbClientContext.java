/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.netflix.astyanax.connectionpool.SSLConnectionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.coordinator.client.model.Constants;
import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.Host;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolType;
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl;
import com.netflix.astyanax.model.ConsistencyLevel;
import com.netflix.astyanax.partitioner.Murmur3Partitioner;
import com.netflix.astyanax.partitioner.Partitioner;
import com.netflix.astyanax.retry.RetryPolicy;
import com.netflix.astyanax.thrift.ThriftFamilyFactory;

public class DbClientContext {

    private static final Logger _log = LoggerFactory.getLogger(DbClientContext.class);

    private static final int DEFAULT_MAX_CONNECTIONS = 64;
    private static final int DEFAULT_MAX_CONNECTIONS_PER_HOST = 14;
    private static final int DEFAULT_SVCLIST_POLL_INTERVAL_SEC = 5;
    private static final int DEFAULT_CONN_TIMEOUT = 1000 * 5;
    private static final int DEFAULT_MAX_BLOCKED_THREADS = 500;
    private static final String DEFAULT_CN_POOL_NANE = "DbClientPool";
    private static final long DEFAULT_CONNECTION_POOL_MONITOR_INTERVAL = 1000;
    private static final int MAX_QUERY_RETRY = 5;
    private static final int QUERY_RETRY_SLEEP_SECONDS = 1000;
    
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
    
    private AstyanaxContext<Keyspace> context;
    private Keyspace keyspace;

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
        if (context == null) {
            throw new IllegalStateException();
        }
        return keyspace;
    }

    public void setHosts(Collection<Host> hosts) {
        if (context == null) {
            throw new IllegalStateException();
        }
        context.getConnectionPool().setHosts(hosts);
    }
    
    public int getPort() {
        return context.getConnectionPoolConfiguration().getPort();
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
        this. maxConnections = maxConnections;
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
        _log.info ("Initializing hosts for {}", svcName );
        List<Host> hosts = hostSupplier.get();
        if((hosts != null) && (hosts.isEmpty())) {
            throw new IllegalStateException(String.format("DbClientContext.init() : host list in hostsupplier for %s is empty", svcName));
        } else {
        	int hostCount = hosts==null? 0 : hosts.size();
            _log.info(String.format("number of hosts in the hostsupplier for %s is %d", svcName, hostCount));
        }
        Partitioner murmur3partitioner = Murmur3Partitioner.get();
        Map<String, Partitioner> partitioners = new HashMap<String, Partitioner>();
        partitioners.put("org.apache.cassandra.dht.Murmur3Partitioner.class.getCanonicalName()",
                murmur3partitioner);
        
        ConsistencyLevel readCL = ConsistencyLevel.CL_QUORUM;
        ConsistencyLevel writeCL = ConsistencyLevel.CL_QUORUM;

        // Set different consistency level for goedbsvc
        if (Constants.GEODBSVC_NAME.equals(svcName)) {
            readCL = ConsistencyLevel.CL_LOCAL_QUORUM;
            writeCL = ConsistencyLevel.CL_EACH_QUORUM;
        }

        ConnectionPoolConfigurationImpl cfg = new ConnectionPoolConfigurationImpl(DEFAULT_CN_POOL_NANE).setMaxConns(maxConnections)
                .setMaxConnsPerHost(maxConnectionsPerHost).setConnectTimeout(DEFAULT_CONN_TIMEOUT)
                .setMaxBlockedThreadsPerHost(DEFAULT_MAX_BLOCKED_THREADS).setPartitioner(murmur3partitioner);

        _log.info("The client to node is encrypted={}", isClientToNodeEncrypted);
        if (isClientToNodeEncrypted) {
            SSLConnectionContext sslContext = getSSLConnectionContext();
            cfg.setSSLConnectionContext(sslContext);
        }

        // TODO revisit it to see if we need set different retry policy, timeout, discovery delay etc for geodb
        context = new AstyanaxContext.Builder().withHostSupplier(hostSupplier)
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
        context.start();
        keyspace = context.getClient();
        initDone = true;
    }

    public SSLConnectionContext getSSLConnectionContext() {
        List<String> cipherSuites = new ArrayList(1);
        cipherSuites.add(cipherSuite);

        return new SSLConnectionContext(trustStoreFile, trustStorePassword, SSLConnectionContext.DEFAULT_SSL_PROTOCOL, cipherSuites);
    }

    public synchronized void stop() {
        if (context == null) {
            throw new IllegalStateException();
        }

        context.shutdown();
        context = null;
    }
}
