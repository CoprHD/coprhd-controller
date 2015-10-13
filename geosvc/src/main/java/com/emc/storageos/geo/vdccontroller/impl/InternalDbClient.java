/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.geo.vdccontroller.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.management.jmx.recovery.DbManagerOps;
import com.emc.vipr.model.sys.recovery.DbRepairStatus;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.ddl.KeyspaceDefinition;
import org.apache.cassandra.locator.EndpointSnitchInfoMBean;
import org.apache.cassandra.service.StorageServiceMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.model.VirtualDataCenter.ConnectionStatus;
import com.emc.storageos.db.client.model.VirtualDataCenter.GeoReplicationStatus;
import com.emc.storageos.db.client.util.KeyspaceUtil;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.server.geo.GeoInternodeAuthenticatorMBean;

/**
 * Internal db client used for geosvc
 */
public class InternalDbClient extends DbClientImpl {
    private static final Logger log = LoggerFactory.getLogger(InternalDbClient.class);
    private static final int WAIT_INTERVAL_IN_SEC = 60;
    private static final int DB_RING_TIMEOUT = 10 * 60 * 1000; // 10 mins
    private static final int DB_STABLE_TIMEOUT = 30 * 60 * 1000; // 30 mins
    private static final int WAIT_QUERY_NODE_REPAIR_BEGIN = 5 * 60 * 1000; // query every 5min
    private static final int WAIT_QUERY_NODE_REPAIR_PROGRESS = 5 * 1000; // query every 5S
    private static String LOCALHOST = "127.0.0.1";

    @Deprecated
    public String getMyVdcId() {
        return VdcUtil.getLocalShortVdcId();
    }

    /**
     * Initialize local db context only. Geodb context will be initialized on demand
     */
    protected void setupContext() {
        if (localContext != null) {
            setupContext(localContext, Constants.DBSVC_NAME);
        }
    }

    protected Keyspace getGeoKeyspace() {
        if (geoContext != null && !geoContext.isInitDone()) {
            setupContext(geoContext, Constants.GEODBSVC_NAME);
        }
        return geoContext.getKeyspace();
    }

    protected <T extends DataObject> Keyspace getKeyspace(Class<T> clazz) {
        DbClientContext ctx = null;
        if (localContext == null || geoContext == null) {
            throw new IllegalStateException();
        }
        ctx = KeyspaceUtil.isGlobal(clazz) ? geoContext : localContext;
        if (!ctx.isInitDone()) {
            String serviceName = ctx.equals(geoContext) ? Constants.GEODBSVC_NAME : Constants.DBSVC_NAME;
            log.info("Initialize db context {}", serviceName);
            setupContext(ctx, serviceName);
        }

        return ctx.getKeyspace();
    }

    /**
     * Waits for the db instances joined in all sites
     */
    public void waitAllSitesDbStable() {
        String prefix = "Waiting for DB cluster become stable on all sites ...";
        log.info(prefix);

        DbJmxClient geoInstance = getJmxClient(LOCALHOST);

        // Loop all VDC
        List<URI> vdcIdIter = queryByType(VirtualDataCenter.class, true);
        for (URI vdcId : vdcIdIter) {
            log.info("loop db status check on {}", vdcId.toString());
            VirtualDataCenter vdc = queryObject(VirtualDataCenter.class, vdcId);

            // filter out vdcs that are not connected in geo
            if (!shouldCheckDbStatus(vdc)) {
                log.error("ignore vdc for db status check {}", vdcId);
                continue;
            }
            if (vdc.getConnectionStatus() != ConnectionStatus.DISCONNECTED) {
                waitDbNodesStable(geoInstance, vdc.getShortId(), vdc.getHostCount()); // short Id
            }
        }
    }

    /**
     * Check if we need wait for geodbsvc up on given vdc
     */
    private boolean shouldCheckDbStatus(VirtualDataCenter vdc) {
        // local vdc is always connected with itself
        if (vdc.getLocal()) {
            return true;
        }

        // incomplete vdc record
        if ((vdc.getShortId() == null) || (vdc.getHostCount() == null)) {
            log.error("invalid record in db status check {}", vdc.getId());
            return false;
        }

        ConnectionStatus connStatus = vdc.getConnectionStatus();
        GeoReplicationStatus repStatus = vdc.getRepStatus();
        log.info("vdc connectionStatus {} repStatus {}", connStatus, repStatus);
        // geodb connected
        if (repStatus.equals(GeoReplicationStatus.REP_ALL)) {
            log.info("vdc {}, repStatus {}", vdc.getId(), repStatus);
            return true;
        }
        // connecting now, check db stable status as well
        if (connStatus.equals(ConnectionStatus.CONNECTING_SYNCED)) {
            return true;
        }
        return false;
    }

    /**
     * Stop gossiping on geodb of current cluster
     */
    public void stopClusterGossiping() {
        DbJmxClient localClient = getJmxClient(LOCALHOST);
        List<String> liveNodes = localClient.getDcLiveNodes(VdcUtil.getLocalShortVdcId());
        for (String ip : liveNodes) {
            log.info("Stop gossiping for {}", ip);
            ip = getEffectiveAddress(ip);
            try {
                DbJmxClient client = getJmxClient(ip);
                client.stopGossiping();
            } catch (Exception ignored) {
                log.error("Ignored: stop gossiping failed on node {}", ip);
            }
        }
    }

    private DbJmxClient getJmxClient(String ip) {
        ip = getEffectiveAddress(ip);

        DbJmxClient geoInstance = null;
        try {
            geoInstance = new DbJmxClient(ip, DbJmxClient.DEFAULTGEOPORT);
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Not able to connect via JMX %s", ip));
        }
        return geoInstance;
    }

    private String getEffectiveAddress(String ip) {
        if ((!ip.startsWith("[")) && ip.contains(":")) {
            ip = "[" + ip + "]";
        }
        return ip;
    }

    /**
     * Wait for num of db instances joined in a vdc.
     * 
     * @param geoInstance jmx client
     * @param vdcShortId the short id of vdc
     * @param vdcHosts the total hosts of a vdc
     */
    public void waitDbNodesStable(DbJmxClient geoInstance, String vdcShortId, int vdcHosts) {
        String prefix = "Waiting for DB cluster become stable for VDC with shortId ' " + vdcShortId + "'...";
        log.info(prefix);
        long start = System.currentTimeMillis();
        // quorum + 1
        // it ensure at least quorum nodes data rebuild done
        int numHosts = (vdcHosts / 2 + 2) > vdcHosts ? vdcHosts : vdcHosts / 2 + 2;
        while (System.currentTimeMillis() - start < DB_STABLE_TIMEOUT) {
            try {
                List<String> liveNodes = geoInstance.getDcLiveNodes(vdcShortId);
                log.info("{} has live nodes of {}", vdcShortId, liveNodes);
                if (liveNodes.size() >= numHosts) {
                    int i = 0;
                    for (String host : liveNodes) {
                        if (!geoInstance.getJoiningNodes().contains(host)
                                && !geoInstance.getLeavingNodes().contains(host)
                                && !geoInstance.getMovingNodes().contains(host)) {
                            log.info("Node {} jumps to NORMAL", host);
                            ++i;
                        }
                    }
                    if (i >= numHosts) {
                        log.info("Living nodes {} meet the requirement: {}", liveNodes.toString(), numHosts);
                        log.info("{} Done", prefix);
                        return;
                    }
                } else {
                    log.info("db {} not meet {} hosts yet", vdcShortId, numHosts);
                }
                TimeUnit.SECONDS.sleep(WAIT_INTERVAL_IN_SEC);
            } catch (InterruptedException ex) {
                // Ignore this exception
            } catch (Exception ex) {
                log.error("Exception checking DB cluster status", ex);
            }
        }
        log.info("{} Timed out", prefix);
        throw new IllegalStateException(String.format("%s : Timed out", prefix));
    }

    /**
     * Wait for db ring rebuild finished in a vdc.
     * Quorum nodes owns full data, rebuild may need a long time
     * 
     * @param vdcShortId the short id of vdc
     * @param vdcHosts total hosts of the vdc
     */
    public void waitDbRingRebuildDone(String vdcShortId, int vdcHosts) {
        String prefix = new StringBuilder("Waiting for DB rebuild to finish for vdc with shortId '").
                append(vdcShortId).append("' and ").
                append(vdcHosts).append(" hosts...").toString();
        log.info(prefix);

        DbJmxClient geoInstance = getJmxClient(LOCALHOST);

        int quorum = vdcHosts / 2 + 1;
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < DB_RING_TIMEOUT) {
            try {
                List<String> fullOwners = geoInstance.getDcNodeFullOwnership(vdcShortId);
                if (fullOwners.size() >= quorum) {
                    log.info("Full owner nodes: {}", fullOwners.toString());
                    return;
                } else {
                    log.info("db {} rebuild not finish yet", vdcShortId);
                }
                TimeUnit.SECONDS.sleep(WAIT_INTERVAL_IN_SEC);
            } catch (InterruptedException ex) {
                // Ignore this exception
            } catch (Exception ex) {
                log.error("Exception checking DB cluster status", ex);
            }
        }
        log.info("{} Timed out", prefix);
        throw new IllegalStateException(String.format("%s : Timed out", prefix));
    }

    /**
     * Wait for a vdc removed from current token ring.
     * 
     * @param vdcShortId the short id of vdc
     */
    public void waitVdcRemoveDone(String vdcShortId) {
        String prefix = String.format("Waiting for vdc removal from cassandra with shortId '%s' ...", vdcShortId);
        log.info(prefix);

        DbJmxClient geoInstance = getJmxClient(LOCALHOST);

        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < DB_RING_TIMEOUT) {
            try {
                if (!geoInstance.isRingOwnedBy(vdcShortId)) {
                    log.info("vdc remove done: {}", vdcShortId);
                    return;
                } else {
                    log.info("vdc removal {} not finish yet", vdcShortId);
                }
                TimeUnit.SECONDS.sleep(WAIT_INTERVAL_IN_SEC);
            } catch (InterruptedException ex) {
                // Ignore this exception
            } catch (Exception ex) {
                log.error("Exception checking DB cluster status", ex);
            }
        }
        log.info("{} Timed out", prefix);
        throw new IllegalStateException(String.format("%s : Timed out", prefix));
    }

    public void removeVdcNodes(VirtualDataCenter vdc) {
        DbJmxClient geoInstance = getJmxClient(LOCALHOST);
        try {
            geoInstance.removeVdc(vdc);
            log.info("The hosts in {} is removed", vdc.getShortId());
        } catch (Exception e) {
            log.error("Failed to remove nodes in vdc {} e=", vdc.getShortId(), e);
        }
    }

    public Map<String, String> getGeoStrategyOptions() throws ConnectionException {
        Keyspace ks = getGeoKeyspace();
        KeyspaceDefinition ksDef = ks.describeKeyspace();

        return ksDef.getStrategyOptions();
    }

    public void runNodeRepairBackEnd(String reconnVdcShortId) throws Exception {
        log.info("Node repair for reconnect operation is starting at vdc {}", reconnVdcShortId);
        DbJmxClient localJmxClient = getJmxClient(LOCALHOST);
        localJmxClient.runNodeRepairBackEnd();
    }

    public Map<String, List<String>> getGeoSchemaVersions() throws ConnectionException {
        Keyspace ks = getGeoKeyspace();
        return ks.describeSchemaVersions();
    }

    public void addVdcNodesToBlacklist(VirtualDataCenter vdc) {
        DbJmxClient localJmxClient = getJmxClient(LOCALHOST);
        List<String> liveNodes = localJmxClient.getDcLiveNodes(VdcUtil.getLocalShortVdcId());
        for (String nodeIp : liveNodes) {
            DbJmxClient jmxClient = getJmxClient(nodeIp);
            jmxClient.addVdcNodesToBlacklist(vdc);
            log.info("Add node to blacklist {}", nodeIp);
        }
    }

    public void clearBlackList() {
        Map<String, List<String>> currBlackList = getBlacklist();
        Set<Map.Entry<String, List<String>>> entrySet = currBlackList.entrySet();
        for (Map.Entry<String, List<String>> entry : entrySet) {
            DbJmxClient jmxClient = getJmxClient(entry.getKey());
            jmxClient.removeVdcNodesFromBlacklist(entry.getValue());
        }
    }

    public void removeVdcNodesFromBlacklist(VirtualDataCenter vdc) {
        DbJmxClient localJmxClient = getJmxClient(LOCALHOST);
        List<String> liveNodes = localJmxClient.getDcLiveNodes(VdcUtil.getLocalShortVdcId());
        for (String nodeIp : liveNodes) {
            DbJmxClient jmxClient = getJmxClient(nodeIp);
            jmxClient.removeVdcNodesFromBlacklist(vdc);
            log.info("Remove node from blacklist {}", nodeIp);
        }
    }

    public Map<String, List<String>> getBlacklist() {
        Map<String, List<String>> result = new HashMap<String, List<String>>();
        DbJmxClient localJmxClient = getJmxClient(LOCALHOST);
        List<String> liveNodes = localJmxClient.getDcLiveNodes(VdcUtil.getLocalShortVdcId());
        for (String nodeIp : liveNodes) {
            DbJmxClient jmxClient = getJmxClient(nodeIp);
            List<String> blacklist = jmxClient.getBlacklist();
            if (!blacklist.isEmpty()) {
                result.put(nodeIp, blacklist);
                log.info("Get blacklist {} for node {}", blacklist, nodeIp);
            }
        }
        return result;
    }

    public boolean isGeoDbClientEncrypted() {
        return geoContext.isClientToNodeEncrypted();
    }

    /**
     * The JMX client of Cassandra
     */
    public static class DbJmxClient {
        private static final String FMTURL = "service:jmx:rmi://%s:7300/jndi/rmi://%s:%d/jmxrmi";
        private static final String SSOBJNAME = "org.apache.cassandra.db:type=StorageService";
        private static final int DEFAULTPORT = 7199;
        private static final int DEFAULTGEOPORT = 7299;
        final String host;
        final int port;
        private String username;
        private String password;

        private JMXConnector jmxc;
        private MBeanServerConnection mbeanServerConn;
        private StorageServiceMBean ssProxy;
        private EndpointSnitchInfoMBean snitchProxy;
        private GeoInternodeAuthenticatorMBean internodeAuthProxy;
        private DbManagerOps dbMgrOps;

        /**
         * Create JMX client using the specified JMX host and port.
         * 
         * @param host hostname or IP address of the JMX agent
         * @param port TCP port of the remote JMX agent
         * @throws IOException on connection failures
         */
        public DbJmxClient(String host, int port) throws IOException, InterruptedException
        {
            this.host = host;
            this.port = port;
            connect();
        }

        /**
         * Create JMX client using the specified JMX host and default port.
         * 
         * @param host hostname or IP address of the JMX agent
         * @throws IOException on connection failures
         */
        public DbJmxClient(String host) throws IOException, InterruptedException
        {
            this.host = host;
            this.port = DEFAULTPORT;
            connect();
        }

        /**
         * Create a connection to the JMX agent and setup the M[X]Bean proxies.
         * 
         * @throws IOException on connection failures
         */
        private void connect() throws IOException {
            JMXServiceURL jmxUrl = new JMXServiceURL(String.format(FMTURL, host, host, port));
            Map<String, Object> env = new HashMap<String, Object>();
            if (username != null) {
                String[] creds = { username, password };
                env.put(JMXConnector.CREDENTIALS, creds);
            }

            jmxc = JMXConnectorFactory.connect(jmxUrl, env);
            mbeanServerConn = jmxc.getMBeanServerConnection();

            try {
                ObjectName name = new ObjectName(SSOBJNAME);
                ssProxy = JMX.newMBeanProxy(mbeanServerConn, name, StorageServiceMBean.class);
                snitchProxy = JMX.newMBeanProxy(mbeanServerConn, new ObjectName("org.apache.cassandra.db:type=EndpointSnitchInfo"),
                        EndpointSnitchInfoMBean.class);
                internodeAuthProxy = JMX.newMBeanProxy(mbeanServerConn, new ObjectName(GeoInternodeAuthenticatorMBean.MBEAN_NAME),
                        GeoInternodeAuthenticatorMBean.class);
                dbMgrOps = new DbManagerOps(mbeanServerConn);
            } catch (MalformedObjectNameException e) {
                throw new RuntimeException(
                        "Invalid ObjectName? Please report this as a bug.", e);
            }
        }

        /**
         * get the live nodes of the dc
         * 
         * @param dcId the vdc shortId
         */
        public List<String> getDcLiveNodes(String dcId) {
            // An easy way is to iterate the host ips, but need figure it out in ipv4/6 support later
            List<String> dcLiveNodes = new ArrayList<String>();

            Map<InetAddress, Float> ownerships;
            try {
                ownerships = effectiveOwnership(null);
            } catch (IllegalStateException ex) {
                ownerships = getOwnership();
            }
            try {

                // go through the list, filter by dc
                for (Map.Entry<InetAddress, Float> ownership : ownerships.entrySet()) {
                    String endpoint = ownership.getKey().getHostAddress();
                    String dc = snitchProxy.getDatacenter(endpoint);

                    if (dc.equals(dcId)) {
                        // check the status of this node
                        if (getLiveNodes().contains(endpoint)) {
                            dcLiveNodes.add(endpoint);
                        }
                    }
                }
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }

            return dcLiveNodes;
        }

        /**
         * get the nodes with full ownership for a given dc
         * 
         * @param dcId the vdc shortId
         */
        public List<String> getDcNodeFullOwnership(String dcId) {
            // An easy way is to iterate the host ips, but need figure it out in ipv4/6 support later
            List<String> fullOwners = new ArrayList<String>();
            Map<InetAddress, Float> ownerships;
            try {
                ownerships = effectiveOwnership(null);
            } catch (IllegalStateException ex) {
                ownerships = getOwnership();
            }
            try {
                // go through the list, filter by dc
                for (Map.Entry<InetAddress, Float> ownership : ownerships.entrySet()) {
                    String endpoint = ownership.getKey().getHostAddress();
                    String dc = snitchProxy.getDatacenter(endpoint);
                    Float owns = ownership.getValue();
                    if (dc.equals(dcId)) {
                        // check if owns full data
                        // due to changes in cassandra v2, ownership is not 100% any more
                        log.info("owns by node {} {}", endpoint, owns);
                        if (owns != null && (owns.compareTo(0.0f) > 0)) {
                            fullOwners.add(endpoint);
                        }
                    }
                }
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
            return fullOwners;
        }

        /**
         * check if the node in given vdc has ownership on token ring
         * 
         * @param dcId the vdc shortId
         */
        public boolean isRingOwnedBy(String dcId) {
            // An easy way is to iterate the host ips, but need figure it out in ipv4/6 support later
            Map<InetAddress, Float> ownerships;
            try {
                ownerships = effectiveOwnership(null);
            } catch (IllegalStateException ex) {
                ownerships = getOwnership();
            }
            try {
                // go through the list, filter by dc
                for (Map.Entry<InetAddress, Float> ownership : ownerships.entrySet()) {
                    String endpoint = ownership.getKey().getHostAddress();
                    String dc = snitchProxy.getDatacenter(endpoint);
                    if (dc.equals(dcId)) {
                        log.info("endpoint {} active on ring", endpoint);
                        return true;
                    }
                }
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
            return false;
        }

        public Map<InetAddress, Float> getOwnership() {
            return ssProxy.getOwnership();
        }

        public Map<InetAddress, Float> effectiveOwnership(String keyspace) throws IllegalStateException {
            return ssProxy.effectiveOwnership(keyspace);
        }

        public List<String> getLiveNodes() {
            return ssProxy.getLiveNodes();
        }

        public List<String> getJoiningNodes() {
            return ssProxy.getJoiningNodes();
        }

        public List<String> getLeavingNodes() {
            return ssProxy.getLeavingNodes();
        }

        public List<String> getMovingNodes() {
            return ssProxy.getMovingNodes();
        }

        public List<String> getUnreachableNodes() {
            return ssProxy.getUnreachableNodes();
        }

        public void stopGossiping() {
            ssProxy.stopGossiping();
        }

        public List<String> getHostIdMap(VirtualDataCenter vdc) {
            List<String> ids = new ArrayList();
            Map<String, String> idsMap = ssProxy.getHostIdMap();

            Collection<String> addrs = vdc.queryHostIPAddressesMap().values();
            for (String addr : addrs) {
                ids.add(idsMap.get(addr));
            }

            return ids;
        }

        public void removeVdc(VirtualDataCenter vdc) {
            List<String> ids = getHostIdMap(vdc);

            for (String id : ids) {
                ssProxy.removeNode(id);
            }
        }

        public void runNodeRepairBackEnd() throws Exception {
            this.dbMgrOps.startNodeRepairAndWaitFinish(false, true);
        }

        public int getNodeRepairProgress() {
            DbRepairStatus status = dbMgrOps.getLastRepairStatus(true);
            if (status == null || status.getStatus() != DbRepairStatus.Status.IN_PROGRESS) {
                return -1;
            }

            return status.getProgress();
        }

        public void addVdcNodesToBlacklist(VirtualDataCenter vdc) {
            Collection<String> addrs = vdc.queryHostIPAddressesMap().values();
            List<String> newBlacklist = new ArrayList<String>();
            newBlacklist.addAll(addrs);
            internodeAuthProxy.addToBlacklist(newBlacklist);
        }

        public void removeVdcNodesFromBlacklist(VirtualDataCenter vdc) {
            Collection<String> addrs = vdc.queryHostIPAddressesMap().values();
            List<String> newBlacklist = new ArrayList<String>();
            newBlacklist.addAll(addrs);
            internodeAuthProxy.removeFromBlacklist(newBlacklist);
        }

        public void removeVdcNodesFromBlacklist(List<String> nodeIPs) {
            internodeAuthProxy.removeFromBlacklist(nodeIPs);
        }

        public List<String> getBlacklist() {
            return internodeAuthProxy.getBlacklist();
        }
    }
}
