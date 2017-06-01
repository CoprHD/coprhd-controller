/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.cassandra.service.StorageProxy;
import org.apache.cassandra.service.StorageServiceMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SocketOptions;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.WriteType;
import com.datastax.driver.core.exceptions.ConnectionException;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.policies.RetryPolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.db.exceptions.DatabaseException;

public class DbClientContext {
    
    private static final int RETRY_POLICY_INTERVAL_EVERY_RETRY = 1000;

    private static final int RETRY_POLICY_MAX_TIMES = 10;

    private static final Logger log = LoggerFactory.getLogger(DbClientContext.class);

    private static final int DEFAULT_MAX_CONNECTIONS = 64;
    private static final int DEFAULT_MAX_CONNECTIONS_PER_HOST = 14;
    private static final int DEFAULT_SVCLIST_POLL_INTERVAL_SEC = 5;
    private static final int DEFAULT_CONN_TIMEOUT = 1000 * 5;
    private static final int DEFAULT_SOCKET_READ_TIMEOUT = 1000 * 15;
    private static final int DEFAULT_MAX_BLOCKED_THREADS = 500;
    private static final String DEFAULT_CN_POOL_NANE = "DbClientPool";
    private static final long DEFAULT_CONNECTION_POOL_MONITOR_INTERVAL = 1000;
    private static final int MAX_QUERY_RETRY = 5;
    private static final int QUERY_RETRY_SLEEP_MS = 1000;
    private static final String LOCAL_HOST = "localhost";

    private static final String KEYSPACE_NETWORK_TOPOLOGY_STRATEGY = "NetworkTopologyStrategy";
    private static final int DEFAULT_CONSISTENCY_LEVEL_CHECK_SEC = 30;

    public static final String LOCAL_CLUSTER_NAME = "StorageOS";
    public static final String LOCAL_KEYSPACE_NAME = "StorageOS";
    public static final String GEO_CLUSTER_NAME = "GeoStorageOS";
    public static final String GEO_KEYSPACE_NAME = "GeoStorageOS";
    public static final long MAX_SCHEMA_WAIT_MS = 60 * 1000 * 10; // 10 minutes
    public static final int SCHEMA_RETRY_SLEEP_MILLIS = 10 * 1000; // 10 seconds
    
    private static final String CASSANDRA_HOST_STATE_DOWN = "DOWN";
    
    private static final ConsistencyLevel DEFAULT_READ_CONSISTENCY_LEVEL = ConsistencyLevel.LOCAL_QUORUM;
    private static final ConsistencyLevel DEFAULT_WRITE_CONSISTENCY_LEVEL = ConsistencyLevel.EACH_QUORUM;

    private static final int REQUEST_WARNING_THRESHOLD_COUNT = 20000;
    private static final int REQUEST_MONITOR_INTERVAL_SECOND = 60;

    private int maxConnections = DEFAULT_MAX_CONNECTIONS;
    private int maxConnectionsPerHost = DEFAULT_MAX_CONNECTIONS_PER_HOST;
    private int svcListPoolIntervalSec = DEFAULT_SVCLIST_POLL_INTERVAL_SEC;
    private long monitorIntervalSecs = DEFAULT_CONNECTION_POOL_MONITOR_INTERVAL;
    private String keyspaceName = LOCAL_KEYSPACE_NAME;
    private String clusterName = LOCAL_CLUSTER_NAME;

    private boolean initDone = false;
    private String cipherSuite;
    private String keyStoreFile;
    private String trustStoreFile;
    private String trustStorePassword;
    private boolean isClientToNodeEncrypted;
    private int logInterval = 1800; //seconds
    private ScheduledExecutorService exe = Executors.newScheduledThreadPool(1);

    // whether to retry once with LOCAL_QUORUM for write failure 
    private boolean retryFailedWriteWithLocalQuorum = false; 
    
    public static final int DB_NATIVE_TRANSPORT_PORT = 9042;
    public static final int GEODB_NATIVE_TRANSPORT_PORT = 9043;
    private static final int CASSANDRA_GEODB_JMX_PORT = 7299;
    private static final int CASSANDRA_DB_JMX_PORT = 7199;
    
    private Cluster cassandraCluster;
    private Session cassandraSession;
    private Map<String, PreparedStatement> prepareStatementMap = new HashMap<String, PreparedStatement>();
    private ConsistencyLevel writeConsistencyLevel = DEFAULT_WRITE_CONSISTENCY_LEVEL;
    
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
    
    public com.datastax.driver.core.Cluster getCassandraCluster() {
        if (cassandraCluster == null) {
        	initClusterContext();
        }
        return cassandraCluster;
    }

    public void setLogInterval(int interval) {
        this.logInterval = interval;
    }

    public int getLogInterval() {
        return logInterval;
    }

    public boolean isRetryFailedWriteWithLocalQuorum() {
        return retryFailedWriteWithLocalQuorum;
    }

    public void setRetryFailedWriteWithLocalQuorum(boolean retryFailedWriteWithLocalQuorum) {
        this.retryFailedWriteWithLocalQuorum = retryFailedWriteWithLocalQuorum;
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
        List<CassandraHost> hosts = hostSupplier.get();
        
        if ((hosts != null) && (hosts.isEmpty())) {
            throw new IllegalStateException(String.format("DbClientContext.init() : host list in hostsupplier for %s is empty", svcName));
        } else {
            int hostCount = hosts == null ? 0 : hosts.size();
            log.info(String.format("number of hosts in the hostsupplier for %s is %d", svcName, hostCount));
        }

        // Check and reset default write consistency level
        final DrUtil drUtil = new DrUtil(hostSupplier.getCoordinatorClient());
        if (drUtil.isMultivdc()) {
            setRetryFailedWriteWithLocalQuorum(false); // geodb in mutlivdc should be EACH_QUORUM always. Never retry for write failures
            log.info("Retry for failed write with LOCAL_QUORUM: {}", retryFailedWriteWithLocalQuorum);
        } else {
            setRetryFailedWriteWithLocalQuorum(true);
        }
        if (drUtil.isActiveSite() && !drUtil.isMultivdc()) {
            log.info("Schedule db consistency level monitor on DR active site");
            exe.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    try {
                        checkAndResetConsistencyLevel(drUtil, hostSupplier.getDbSvcName());
                    } catch (Exception ex) {
                        log.warn("Encounter Unexpected exception during check consistency level. Retry in next run", ex);
                    }
                }
            }, 60, DEFAULT_CONSISTENCY_LEVEL_CHECK_SEC, TimeUnit.SECONDS);
        }
        
        PoolingOptions poolingOptions = new PoolingOptions();
        poolingOptions.setMaxRequestsPerConnection(HostDistance.LOCAL, maxConnections);
        poolingOptions.setMaxConnectionsPerHost(HostDistance.LOCAL, maxConnectionsPerHost);
        poolingOptions.setPoolTimeoutMillis(DEFAULT_CONN_TIMEOUT);
        
        String[] contactPoints = new String[hosts.size()];
        for (int i = 0; i < hosts.size(); i++) {
            contactPoints[i] = hosts.get(i).getHost();
        }
        cassandraCluster = com.datastax.driver.core.Cluster
                .builder()
                .withPoolingOptions(poolingOptions)
                .withRetryPolicy(new ViPRRetryPolicy(RETRY_POLICY_MAX_TIMES, RETRY_POLICY_INTERVAL_EVERY_RETRY))
                .withLoadBalancingPolicy(new RoundRobinPolicy())
                .addContactPoints(contactPoints).withPort(getNativeTransportPort())
                .withSocketOptions(new SocketOptions().setConnectTimeoutMillis(DEFAULT_CONN_TIMEOUT).setReadTimeoutMillis(DEFAULT_SOCKET_READ_TIMEOUT))
                .build();
        cassandraCluster.getConfiguration().getQueryOptions().setConsistencyLevel(DEFAULT_READ_CONSISTENCY_LEVEL);
        cassandraSession = cassandraCluster.connect("\"" + keyspaceName + "\"");
        prepareStatementMap = new HashMap<String, PreparedStatement>();
        initDone = true;
    }

    private Cluster initConnection(String[] contactPoints) {
        return Cluster
                .builder()
                .addContactPoints(contactPoints).withPort(getNativeTransportPort())
                .withClusterName(clusterName)
                .withLoadBalancingPolicy(new RoundRobinPolicy())
                .withRetryPolicy(new ViPRRetryPolicy(10, 1000))
                .build();
    }

    public KeyspaceMetadata getKeyspaceMetaData() {
        if (cassandraCluster == null) {
            initClusterContext();
        }
        return cassandraCluster.getMetadata().getKeyspace("\"" + keyspaceName + "\"");
    }

    public void createCF(List<String> cqlList) throws InterruptedException, ExecutionException {
        
        List<ResultSetFuture> futures = new ArrayList<ResultSetFuture>();
        for (String cql : cqlList) {
            log.info("create table with CQL: {}", cql);
            futures.add(cassandraSession.executeAsync(cql));
        }

        for (ResultSetFuture future : futures) {
            future.get();
        }
    }

    

    /**
     * Initialize the cluster context and cluster instances.
     * This has to be separated from init() because dbsvc need this to start
     * while init() depends on dbclient which in turn depends on dbsvc.
     */
    private void initClusterContext() {
        String[] contactPoints = {LOCAL_HOST};
        cassandraCluster = initConnection(contactPoints);
        String keyspaceString = String.format("\"%s\"", keyspaceName);
        if (cassandraCluster.getMetadata().getKeyspace(keyspaceString) == null) {
            cassandraSession = cassandraCluster.connect();
        } else {
            cassandraSession = cassandraCluster.connect(keyspaceString);
        }
        prepareStatementMap = new HashMap<String, PreparedStatement>();
    }

    /**
     * Check if it is geodbsvc
     *
     * @return
     */
    public boolean isGeoDbsvc() {
        return getKeyspaceName().equals(GEO_KEYSPACE_NAME);
    }

    public synchronized void stop() {
        if (cassandraSession != null) {
            cassandraSession.close();
        }

        if (cassandraCluster != null) {
            cassandraCluster.close();
        }

        exe.shutdownNow();
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
            KeyspaceMetadata keyspaceMetadata = getKeyspaceMetaData();
            
            // ensure a schema agreement before updating the strategy options
            // or else it's destined to fail due to SchemaDisagreementException
            boolean hasUnreachableNodes = ensureSchemaAgreement();

            if (keyspaceMetadata != null) {
                updateKeySpace(strategyOptions, "alter");
            } else {
                updateKeySpace(strategyOptions, "create");
                if (wait && !hasUnreachableNodes) {
                    waitForSchemaAgreement();
                }
                return;
            }
    
            if (wait && !hasUnreachableNodes) {
                waitForSchemaAgreement();
            }
        } catch (ConnectionException ex) {
            log.error("Fail to update strategy option", ex);
            throw DatabaseException.fatals.failedToChangeStrategyOption(ex.getMessage());
        }
    }

    private void updateKeySpace(Map<String, String> strategyOptions, String action) {
        StringBuilder replications = new StringBuilder();
        boolean appendComma = false;

        for (Map.Entry<String, String> option : strategyOptions.entrySet()) {
            if (appendComma == false) {
                appendComma = true;
            }else {
                replications.append(",");
            }

            replications.append("'")
                    .append(option.getKey())
                    .append("' : ")
                    .append(option.getValue());
        }

        String createKeySpace=String.format("%s KEYSPACE \"%s\" WITH replication = { 'class': '%s', %s };",
                action, keyspaceName, KEYSPACE_NETWORK_TOPOLOGY_STRATEGY, replications.toString());
        log.info("update keyspace using the cql statement:{}", createKeySpace);
        cassandraSession.execute(createKeySpace);
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
            strategyOptions = getStrategyOptions();
        } catch (DriverException ex) {
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
     * Try to reach a schema agreement among all the reachable nodes
     *
     * @return true if there are unreachable nodes
     */
    public boolean ensureSchemaAgreement() {
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
            if (schemas.size() == 1) {
                if (!schemas.containsKey(StorageProxy.UNREACHABLE)) {
                    return false;
                } else {
                    // keep waiting if all nodes are unreachable
                    continue;
                }
            }
            // schema.size() == 2, if one of them is UNREACHABLE, return
            if (schemas.containsKey(StorageProxy.UNREACHABLE)) {
                return true;
            }
            // else continue waiting
        }
        log.error("Unable to converge schema versions {}", schemas);
        throw new IllegalStateException("Unable to converge schema versions");
    }

    /**
     * Return if there's only one version which is specified target version across the cluster
     * Don't check if all nodes are of this version
     */
    public void waitForSchemaAgreement() {
        waitForSchemaAgreement(-1);
    }

    /**
     * Waits for schema change to propagate through all nodes of cluster
     * It doesn't check if all nodes are of this version when nodeCount == -1
     *
     * @param targetSchemaVersion version we are waiting for
     * @throws InterruptedException
     */
    public void waitForSchemaAgreement(int nodeCount) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < MAX_SCHEMA_WAIT_MS) {
            if (cassandraCluster.getMetadata().checkSchemaAgreement()) {
                log.info("schema agreement achieved");
                return;
            }
            
            //TODO revivit these logic later
            /*if (nodeCount != -1) { // need to check if all nodes converged to target version
                List<String> hosts = null;
                for (Entry<String, List<String>> entry : versions.entrySet()) {
                    hosts = entry.getValue();
                }
                if (hosts != null && hosts.size() == nodeCount) {
                    log.info("schema versions converged, required node count achieved: {}", nodeCount);
                    return;
                }
            } else {
                log.info("schema versions converged, no check for node count");
                return;
            }*/

            log.info("waiting for schema change ...");
            try {
                Thread.sleep(SCHEMA_RETRY_SLEEP_MILLIS);
            } catch (InterruptedException ex) {}
        }

        log.warn("Unable to achieve schema agressment");
    }

    /**
     * Get Cassandra schema versions -> nodes mapping.
     *
     * @return
     */
    public Map<String, List<String>> getSchemaVersions() {
        Map<String, List<String>> versions = new HashMap<String, List<String>>();
        try {
            Set<String> liveNodes = getLiveNodes();
            versions.put(StorageProxy.UNREACHABLE, new LinkedList<String>());
            log.info("Live nodes:" + liveNodes);
            
            ResultSet result = cassandraSession.execute("select * from system.peers");
            for (Row row : result) {
                String hostAddress = row.getInet("rpc_address").getHostAddress();
                if (liveNodes.contains(hostAddress)) {
                    versions.putIfAbsent(row.getUUID("schema_version").toString(), new LinkedList<String>());
                    versions.get(row.getUUID("schema_version").toString()).add(hostAddress);
                } else {
                    versions.get(StorageProxy.UNREACHABLE).add(hostAddress);
                }
            }
            
            result = cassandraSession.execute("select * from system.local");
            Row localNode = result.one();
            versions.putIfAbsent(localNode.getUUID("schema_version").toString(), new LinkedList<String>());
            versions.get(localNode.getUUID("schema_version").toString()).add(localNode.getInet("broadcast_address").getHostAddress());
            
        } catch (ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }

        log.info("schema versions found: {}", versions);
        return versions;
    }

    public Map<String, String> getStrategyOptions() {
        Map<String, String> result = new HashMap<String, String>();
        KeyspaceMetadata keyspaceMetadata = cassandraCluster.getMetadata().getKeyspace("\""+this.getKeyspaceName()+"\"");
        Map<String, String> replications = keyspaceMetadata.getReplication();
        for (Map.Entry<String, String> entry : replications.entrySet()) {
            if (!entry.getKey().startsWith("class")) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        
        return result;
    }

    private void checkAndResetConsistencyLevel(DrUtil drUtil, String svcName) {
        
        if (isRetryFailedWriteWithLocalQuorum() && drUtil.isMultivdc()) {
            log.info("Disable retry for write failure in multiple vdc configuration");
            setRetryFailedWriteWithLocalQuorum(false);
            return;
        }
        
        ConsistencyLevel currentConsistencyLevel = writeConsistencyLevel;
        if (currentConsistencyLevel == ConsistencyLevel.EACH_QUORUM) {
            log.debug("Write consistency level is EACH_QUORUM. No need adjust");
            return;
        }
        
        log.info("Db consistency level for {} is downgraded as LOCAL_QUORUM. Check if we need reset it back", svcName);
        for(Site site : drUtil.listStandbySites()) {
            if (site.getState().equals(SiteState.STANDBY_PAUSED) ||
                    site.getState().equals(SiteState.STANDBY_DEGRADED)) {
                continue; // ignore a standby site which is paused by customer explicitly
            }
            String siteUuid = site.getUuid();
            int count = drUtil.getNumberOfLiveServices(siteUuid, svcName);
            if (count <= site.getNodeCount() / 2) {
                log.info("Service {} of quorum nodes on site {} is down. Still keep write consistency level to LOCAL_QUORUM", svcName, siteUuid);
                return;
            }      
        }
        log.info("Service {} of quorum nodes on all standby sites are up. Reset default write consistency level back to EACH_QUORUM", svcName);
        writeConsistencyLevel = ConsistencyLevel.EACH_QUORUM;
    }
    
    protected int getNativeTransportPort() {
        int port = isGeoDbsvc() ? GEODB_NATIVE_TRANSPORT_PORT : DB_NATIVE_TRANSPORT_PORT;
        return port;
    }

    public Session getSession() {
        if (cassandraCluster == null || cassandraCluster.isClosed()) {
            this.initClusterContext();
        }
        return cassandraSession;
    }
    
    public PreparedStatement getPreparedStatement(String queryString) {
        if (!prepareStatementMap.containsKey(queryString)) {
            PreparedStatement statement = getSession().prepare(queryString);
            prepareStatementMap.put(queryString, statement);
        }
        return prepareStatementMap.get(queryString);
    }

    public ConsistencyLevel getWriteConsistencyLevel() {
        return writeConsistencyLevel;
    }

    public void setWriteConsistencyLevel(ConsistencyLevel writeConsistencyLevel) {
        this.writeConsistencyLevel = writeConsistencyLevel;
    }
    
	private Set<String> getLiveNodes() {
        Set<Host> hosts = cassandraCluster.getMetadata().getAllHosts(); 
        int port = getKeyspaceName() == DbClientContext.LOCAL_KEYSPACE_NAME ? CASSANDRA_DB_JMX_PORT : CASSANDRA_GEODB_JMX_PORT;
        String urlFormat = "service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi";
        
        for (Host host : hosts) {
            try (JMXConnector jmxConnector = JMXConnectorFactory.connect(
                    new JMXServiceURL(String.format(urlFormat, host, port)))){
                
                MBeanServerConnection mbeanServerConnection = jmxConnector.getMBeanServerConnection();
                
                ObjectName mbeanName = new ObjectName("org.apache.cassandra.db:type=StorageService");
                StorageServiceMBean mbeanProxy =
                    (StorageServiceMBean) MBeanServerInvocationHandler.newProxyInstance(
                        mbeanServerConnection, mbeanName, StorageServiceMBean.class, true);
                
                return new HashSet<String>(mbeanProxy.getLiveNodes());
            } catch (Exception e) {
                log.warn("Failed to get unreachable nodes with error {}", e);
            }
        }
        
        return Collections.emptySet();
	}
    
    private class ViPRRetryPolicy implements RetryPolicy {
        private int maxRetry;
        private int sleepInMS;

        public ViPRRetryPolicy(int maxRetry, int sleepInMS) {
            this.maxRetry = maxRetry;
            this.sleepInMS = sleepInMS;
        }

        public RetryDecision onReadTimeout(Statement statement, com.datastax.driver.core.ConsistencyLevel cl, int requiredResponses, int receivedResponses, boolean dataRetrieved, int nbRetry) {
            log.warn("onReadTimeout statement={} retried={} maxRetry={}", statement, nbRetry, maxRetry);
            if (nbRetry == maxRetry)
                return RetryDecision.rethrow();

            delay();

            return RetryDecision.retry(cl);
        }

        private void delay() {
            try {
                Thread.sleep(sleepInMS);
            } catch (InterruptedException e) {
                //ignore
            }
        }

        public RetryDecision onWriteTimeout(Statement statement, com.datastax.driver.core.ConsistencyLevel cl, WriteType writeType, int requiredAcks, int receivedAcks, int nbRetry) {
            log.warn("write timeout statement={} retried={} maxRetry={}", statement, nbRetry, maxRetry);
            if (nbRetry == maxRetry)
                return RetryDecision.rethrow();

            delay();
            // If the batch log write failed, retry the operation as this might just be we were unlucky at picking candidates
            return RetryDecision.retry(cl);
        }

        public RetryDecision onUnavailable(Statement statement, com.datastax.driver.core.ConsistencyLevel cl, int requiredReplica, int aliveReplica, int nbRetry) {
            log.warn("onUnavailable statement={} retried={} maxRetry={}", statement, nbRetry, maxRetry);
            if (nbRetry == maxRetry) {
                return RetryDecision.rethrow();
            }

            delay();
            return RetryDecision.tryNextHost(cl);
        }

        public RetryDecision onRequestError(Statement statement, com.datastax.driver.core.ConsistencyLevel cl, DriverException e, int nbRetry) {
            log.warn("onRequestError statement={} retried={} maxRetry={}", statement, nbRetry, maxRetry);
            if (nbRetry == maxRetry) {
                return RetryDecision.rethrow();
            }

            delay();
            return RetryDecision.tryNextHost(cl);

        }

        public void init(com.datastax.driver.core.Cluster cluster) {
            // nothing to do
        }

        public void close() {
            // nothing to do
        }
    }
}
