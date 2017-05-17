/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.impl;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteMonitorResult;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.management.jmx.recovery.DbManagerMBean;
import com.emc.storageos.management.jmx.recovery.DbManagerOps;
import com.emc.storageos.services.util.JmxServerWrapper;
import com.emc.vipr.model.sys.recovery.DbRepairStatus;
import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.db.HintedHandOffManager;
import org.apache.cassandra.db.SystemKeyspace;
import org.apache.cassandra.dht.Token;
import org.apache.cassandra.gms.ApplicationState;
import org.apache.cassandra.gms.EndpointState;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.gms.IEndpointStateChangeSubscriber;
import org.apache.cassandra.gms.VersionedValue;
import org.apache.cassandra.locator.IEndpointSnitch;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.utils.UUIDGen;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jmx.export.annotation.ManagedResource;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * MBean implementation for all db management operations
 */
@ManagedResource(objectName = DbManagerOps.MBEAN_NAME, description = "DB Manager MBean")
public class DbManager implements DbManagerMBean {
    private static final Logger log = LoggerFactory.getLogger(DbManager.class);

    private static final int REPAIR_INITIAL_WAIT_FOR_DBSTART_MINUTES = 5;
    // repair every 24*5 hours by default, given we do a proactive repair on start
    // once per five days on demand should suffice
    private static final int DEFAULT_DB_REPAIR_FREQ_MIN = 60 * 24 * 5;
    // a normal node removal should succeed in 30s.
    private static final int REMOVE_NODE_TIMEOUT_MILLIS = 1 * 60 * 1000; // 1 min
    private int repairFreqMin = DEFAULT_DB_REPAIR_FREQ_MIN;

    private CoordinatorClient coordinator;
    private SchemaUtil schemaUtil;
    private DrUtil drUtil;
    private ScheduledExecutorService dbNodeStateCallbackExecutor;
    
    @Autowired
    private JmxServerWrapper jmxServer;

    ScheduledFuture<?> scheduledRepairTrigger;

    // Max retry times after a db repair failure
    private int repairRetryTimes = 5;
    private ScheduledExecutorService executor = new NamedScheduledThreadPoolExecutor("DbRepairPool", 3);
    
    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public void setSchemaUtil(SchemaUtil schemaUtil) {
        this.schemaUtil = schemaUtil;
    }

    public void setDrUtil(DrUtil drUtil) {
        this.drUtil = drUtil;
    }
    
    /**
     * Regular repair frequency in minutes
     * 
     * @param repairFreqMin
     */
    public void setRepairFreqMin(int repairFreqMin) {
        this.repairFreqMin = repairFreqMin;
    }

    /**
     * Start a node repair
     * 
     * @param keySpaceName
     * @param maxRetryTimes
     * @param crossVdc
     * @param noNewReapir
     * @return
     * @throws Exception
     */
    private boolean startNodeRepair(String keySpaceName, int maxRetryTimes, boolean crossVdc, boolean noNewReapir) throws Exception {
        DbRepairRunnable runnable = new DbRepairRunnable(jmxServer, this.executor, this.coordinator, keySpaceName,
                this.schemaUtil.isGeoDbsvc(), maxRetryTimes, noNewReapir);
        // call preConfig() here to set IN_PROGRESS for db repair triggered by schedule since we use it in getDbRepairStatus.
        runnable.preConfig();
        synchronized (runnable) {
            this.executor.submit(runnable);
            runnable.wait();
        }

        switch (runnable.getStatus()) {
            case ALREADY_RUNNING:
                return true;
            case NOT_THE_TIME:
                return false;
            case NOTHING_TO_RESUME:
                return false;
        }
        return true;
    }

    private static void addAll(Map<String, Boolean> stateMap, List<String> ips, boolean up) {
        for (String ip : ips) {
            stateMap.put(normalizeInetAddress(ip), up);
        }
    }

    private static String normalizeInetAddress(String ipString) {
        try {
            final DualInetAddress dualAddr = DualInetAddress.fromAddress(ipString);
            return dualAddr.hasInet4() ? dualAddr.getInet4() : dualAddr.getInet6();
        } catch (Exception e) {
            log.error("Failed to normalize ipaddr: {}", ipString, e);
            return null;
        }
    }

    @Override
    public Map<String, Boolean> getNodeStates() {
        Map<String, Boolean> ipStateMap = new TreeMap<>();
        while (true) {
            List<String> upNodes = StorageService.instance.getLiveNodes();
            List<String> downNodes = StorageService.instance.getUnreachableNodes();
            List<String> upNodes2 = StorageService.instance.getLiveNodes();
            if (new HashSet<>(upNodes).equals(new HashSet<>(upNodes2))) {
                addAll(ipStateMap, upNodes, true);
                addAll(ipStateMap, downNodes, false);
                break;
            }
        }

        Map<String, DualInetAddress> idIpMap = this.coordinator.getInetAddessLookupMap().getControllerNodeIPLookupMap();
        Map<String, Boolean> idStateMap = new TreeMap<>();
        for (Map.Entry<String, DualInetAddress> entry : idIpMap.entrySet()) {
            DualInetAddress dualAddr = entry.getValue();
            Boolean state = dualAddr.hasInet4() ? ipStateMap.get(dualAddr.getInet4()) : null;
            if (state == null) {
                state = dualAddr.hasInet6() ? ipStateMap.get(dualAddr.getInet6()) : null;
            }
            if (state != null) {
                idStateMap.put(entry.getKey(), state);
            }
        }

        return idStateMap;
    }

    @Override
    public void removeNode(String nodeId) {
        Map<String, DualInetAddress> idMap = this.coordinator.getInetAddessLookupMap().getControllerNodeIPLookupMap();
        DualInetAddress dualAddr = idMap.get(nodeId);
        if (dualAddr == null) {
            String errMsg = String.format("Cannot find node with name %s", nodeId);
            log.error(errMsg);
            throw new IllegalArgumentException(errMsg);
        }

        Map<String, String> hostIdMap = StorageService.instance.getHostIdMap();
        Map<String, String> ipGuidMap = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : hostIdMap.entrySet()) {
            ipGuidMap.put(normalizeInetAddress(entry.getKey()), entry.getValue());
        }

        String nodeGuid = dualAddr.hasInet4() ? ipGuidMap.get(dualAddr.getInet4()) : null;
        if (nodeGuid == null) {
            nodeGuid = dualAddr.hasInet6() ? ipGuidMap.get(dualAddr.getInet6()) : null;
        }
        if (nodeGuid == null) {
            String errMsg = String.format("Cannot find Cassandra node with IP address %s", dualAddr.toString());
            log.error(errMsg);
            throw new IllegalArgumentException(errMsg);
        }

        log.info("Removing Cassandra node {} on vipr node {}", nodeGuid, nodeId);
        ensureRemoveNode(nodeGuid);
    }

    @Override
    public void startNodeRepair(boolean canResume, boolean crossVdc) throws Exception {
        // The return value is ignored as we are setting interval time to 0, it cannot be NotTheTime. And both AlreadyRunning and Started
        // are considered success. Though the already running repair may not for current cluster state, but that's same if it is and the
        // cluster state changed immediately after that.
        startNodeRepair(this.schemaUtil.getKeyspaceName(), canResume ? this.repairRetryTimes : 0, crossVdc, false);
    }

    private static DbRepairStatus getLastRepairStatus(DbRepairJobState state, String clusterDigest, int maxRetryTime) {
        if (state.getCurrentDigest() != null && (clusterDigest == null || clusterDigest.equals(state.getCurrentDigest()))) {
            if (state.getCurrentRetry() <= maxRetryTime) {
                return new DbRepairStatus(DbRepairStatus.Status.IN_PROGRESS,
                        new Date(state.getCurrentStartTime()), null, state.getCurrentProgress());
            } else {
                return new DbRepairStatus(DbRepairStatus.Status.FAILED,
                        new Date(state.getCurrentStartTime()), new Date(state.getCurrentUpdateTime()),
                        state.getCurrentProgress());
            }
        }

        return getLastSucceededRepairStatus(state, clusterDigest);
    }

    private static DbRepairStatus getLastSucceededRepairStatus(DbRepairJobState state, String clusterDigest) {
        if (state.getLastSuccessDigest() != null && (clusterDigest == null || clusterDigest.equals(state.getLastSuccessDigest()))) {
            return new DbRepairStatus(DbRepairStatus.Status.SUCCESS,
                    new Date(state.getLastSuccessStartTime()), new Date(state.getLastSuccessEndTime()), 100);
        }

        return null;
    }

    @Override
    public DbRepairStatus getLastRepairStatus(boolean forCurrentNodesOnly) {
        try {
            DbRepairJobState state = DbRepairRunnable.queryRepairState(this.coordinator, this.schemaUtil.getKeyspaceName(),
                    this.schemaUtil.isGeoDbsvc());
            log.info("cluster state digest stored in ZK: {}", state.getCurrentDigest());

            DbRepairStatus retState = getLastRepairStatus(state, forCurrentNodesOnly ? DbRepairRunnable.getClusterStateDigest() : null,
                    this.repairRetryTimes);

            if (retState != null && retState.getStatus() == DbRepairStatus.Status.IN_PROGRESS) {
                // See if current state holder is still active, if not, we need to resume it
                String lockName = DbRepairRunnable.getLockName();
                InterProcessLock lock = coordinator.getLock(lockName);

                String currentHolder = DbRepairRunnable.getSelfLockNodeId(lock);
                if (currentHolder == null) { // No thread is actually driving the repair, we need to resume it
                    if (startNodeRepair(this.schemaUtil.getKeyspaceName(), this.repairRetryTimes, false, true)) {
                        log.info("Successfully resumed a previously paused repair");
                    } else {
                        log.warn("Cannot resume a previously paused repair, it could be another thread resumed and finished it");
                    }
                }
            }

            return retState;
        } catch (Exception e) {
            log.error("Failed to get node repair state from ZK", e);
            return null;
        }
    }

    @Override
    public DbRepairStatus getLastSucceededRepairStatus(boolean forCurrentNodesOnly) {
        try {
            DbRepairJobState state = DbRepairRunnable.queryRepairState(this.coordinator, this.schemaUtil.getKeyspaceName(),
                    this.schemaUtil.isGeoDbsvc());

            return getLastSucceededRepairStatus(state, forCurrentNodesOnly ? DbRepairRunnable.getClusterStateDigest() : null);
        } catch (Exception e) {
            log.error("Failed to get node repair state from ZK", e);
            return null;
        }
    }
    
    @Override
    public void resetRepairState() {
        DbRepairRunnable.resetRepairState(this.coordinator, this.schemaUtil.getKeyspaceName(),
                this.schemaUtil.isGeoDbsvc());
    }

    public void start() {
        this.scheduledRepairTrigger = this.executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    startNodeRepair(schemaUtil.getKeyspaceName(), repairRetryTimes, true, false);
                } catch (Exception e) {
                    log.error("Failed to trigger node repair", e);
                }
            }
        }, REPAIR_INITIAL_WAIT_FOR_DBSTART_MINUTES, repairFreqMin, TimeUnit.MINUTES);
    }

    @Override
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

    private void removeCassandraNode(InetAddress nodeIp) {
        Map<String, String> hostIdMap = StorageService.instance.getHostIdMap();
        String guid = hostIdMap.get(nodeIp.getHostAddress());
        // Skip the node removal if this node doesn't exist in host id map 
        if (guid != null) {
            log.info("Removing Cassandra node {} on vipr node {}", guid, nodeIp);
            Gossiper.instance.convict(nodeIp, 0);
            ensureRemoveNode(guid);
        } else {
            log.info("Skip removal of Cassandra node {} due to no host id found", nodeIp);
        }
    }

    /**
     * A safer method to remove Cassandra node. Calls forceRemoveCompletion after REMOVE_NODE_TIMEOUT_MILLIS
     * This will help to prevent node removal from hanging due to CASSANDRA-6542.
     *
     * @param guid
     */
    public void ensureRemoveNode(final String guid) {
        ExecutorService exe = Executors.newSingleThreadExecutor();
        Future<?> future = exe.submit(new Runnable() {
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
        } finally {
            exe.shutdownNow();
        }
    }
    
    /**
     * Check if data is synced with remote data center specified by dcName.  It checks hinted handoff logs
     * 
     * @return true - synced. otherwise false
     */
    @Override
    public boolean isDataCenterSynced(String dcName) {
        log.info("Check if data synced with Cassandra data center {}", dcName);
        
        // Compact HINTS column family and eliminate deleted hints before checking hinted handoff logs
        try {
            StorageService.instance.forceKeyspaceCompaction("system", SystemKeyspace.HINTS_CF);
        } catch (Exception ex) {
            log.warn("Fail to compact system HINTS_CF", ex);
        }
        
        List<InetAddress> allNodes = new ArrayList<>();
        Set<InetAddress> liveNodes = Gossiper.instance.getLiveMembers();
        allNodes.addAll(liveNodes);
        Set<InetAddress> unreachableNodes = Gossiper.instance.getUnreachableMembers();
        allNodes.addAll(unreachableNodes);
        for (InetAddress nodeIp : allNodes) {
            IEndpointSnitch snitch = DatabaseDescriptor.getEndpointSnitch();
            String dc = snitch.getDatacenter(nodeIp);
            if (dc.equals(dcName)) {
                log.info("Checking hinted handoff logs for node {} in data center {} ", nodeIp, dc);
                if (hasPendingHintedHandoff(nodeIp)) {
                    return false;
                }
            }
        }
        return true;
    }
 
    /**
     * Check if there is pending hinted handoff logs for given node
     * 
     * @param endpoint
     * @return true - pending hinted handoff logs exists. Otherwise, false
     */
    private boolean hasPendingHintedHandoff(InetAddress endpoint) {
        List<String> endpointsWithPendingHints = HintedHandOffManager.instance.listEndpointsPendingHints();
        if (endpointsWithPendingHints.isEmpty()) {
            log.info("Skip data sync status check. No pending hinted handoff logs");
            return false;
        }
        log.info("Pending hinted hand off logs found at {}", endpointsWithPendingHints);
        UUID hostId = Gossiper.instance.getHostId(endpoint);
        final ByteBuffer hostIdBytes = ByteBuffer.wrap(UUIDGen.decompose(hostId));
        DecoratedKey epkey =  StorageService.getPartitioner().decorateKey(hostIdBytes);
        Token.TokenFactory tokenFactory = StorageService.getPartitioner().getTokenFactory();
        String token = tokenFactory.toString(epkey.getToken());
        for (String unsyncedEndpoint : endpointsWithPendingHints) {
            if (token.equals(unsyncedEndpoint)) {
                log.info("Unsynced data found for : {}", endpoint);
                return true;
            }
        } 
        return false;
    }

    public void init() {
        if (drUtil.isActiveSite()) {
            log.info("Register Cassandra node state listener on DR active site");
            dbNodeStateCallbackExecutor = Executors.newScheduledThreadPool(1);
            Gossiper.instance.register(endpointStateChangeSubscripter);
        }
    }
    
    private Site getSite(InetAddress endpoint) {
        IEndpointSnitch snitch = DatabaseDescriptor.getEndpointSnitch();
        String dcName = snitch.getDatacenter(endpoint);
        for (Site site : drUtil.listSites()) {
            String cassandraDcId = drUtil.getCassandraDcId(site);
            if (cassandraDcId.equals(dcName)) {
                return site;
            }
        }
        return null;
    }
    
    private void checkAndSetIncrementalSyncing(InetAddress endpoint) {
        Site site = getSite(endpoint);
        if (site == null) {
            log.info("Unknown site for {}. Skip HandoffLogDetector", endpoint);
            return;
        }
        
        log.info("Node {} in site {} comes online", endpoint, site.getUuid());
        if (site.getState() == SiteState.STANDBY_SYNCED) {
            SiteMonitorResult monitorResult = drUtil.getCoordinator().getTargetInfo(site.getUuid(), SiteMonitorResult.class);
            if (monitorResult == null || monitorResult.getDbQuorumLostSince() == 0) {
                log.info("No db quorum lost on standby site. Skip this node up event on {} ", endpoint);
                return;
            }
            if (hasPendingHintedHandoff(endpoint)) { 
                log.info("Hinted handoff logs detected. Change site {} state to STANDBY_INCR_SYNCING", site.getUuid());
                site.setState(SiteState.STANDBY_INCR_SYNCING); 
                drUtil.getCoordinator().persistServiceConfiguration(site.toConfiguration());
            }
        } else {
            log.info("Skip hinted handoff logs detector for {} due to site state is {}. ", endpoint, site.getState());
        }
    }
    
    // Cassandra node state listener
    private IEndpointStateChangeSubscriber endpointStateChangeSubscripter =  new IEndpointStateChangeSubscriber() {
        @Override
        public void onJoin(InetAddress endpoint, EndpointState epState) {
        }

        @Override
        public void beforeChange(InetAddress endpoint, EndpointState currentState, ApplicationState newStateKey,
                VersionedValue newValue) {
        }

        @Override
        public void onChange(InetAddress endpoint, ApplicationState state, VersionedValue value) {
        }

        @Override
        public void onAlive(InetAddress endpoint, EndpointState state) {
            if (drUtil.isStandby()) {
                log.info("Skip node state change of {} on standby site", endpoint);
                return;
            }
            HandoffLogDetector detector = new HandoffLogDetector(endpoint);
            dbNodeStateCallbackExecutor.schedule(detector, 0, TimeUnit.SECONDS);
        }
        
        /**
         * Detect pending handoff logs and set STANDBY_INCR_SYNCING state if necessary
         */
        class HandoffLogDetector implements Runnable {
            private InetAddress endpoint;
            private HandoffLogDetector(InetAddress endpoint) {
                this.endpoint = endpoint;
            }
            public void run() {
                checkAndSetIncrementalSyncing(endpoint);
            }
        };
        
        @Override
        public void onDead(InetAddress endpoint, EndpointState state) {
        }

        @Override
        public void onRemove(InetAddress endpoint) {
        }

        @Override
        public void onRestart(InetAddress endpoint, EndpointState state) {
        }
    };
}
