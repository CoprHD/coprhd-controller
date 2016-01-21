/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.recovery;

import java.net.URI;
import java.util.Calendar;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.emc.storageos.model.property.PropertyConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.leader.LeaderSelector;

import com.emc.vipr.model.sys.ClusterInfo;
import com.emc.vipr.model.sys.recovery.RecoveryStatus;
import com.emc.vipr.model.sys.recovery.DbRepairStatus.Status;
import com.emc.vipr.model.sys.recovery.DbRepairStatus;
import com.emc.vipr.model.sys.recovery.RecoveryConstants;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.coordinator.client.service.impl.LeaderSelectorListenerImpl;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.server.impl.DbRepairRunnable;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.exceptions.SysClientException;
import com.emc.storageos.services.util.NamedThreadPoolExecutor;
import com.emc.storageos.services.util.PlatformUtils;
import com.emc.storageos.management.jmx.recovery.DbManagerOps;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.services.util.MulticastUtil;

/**
 * Recovery Manager drives whole lifecycle of node recovery. It maintains status machine in ZK.
 * See RecoveryStatus.Status on the detailed description of status transition
 */
public class RecoveryManager implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(RecoveryManager.class);

    private List<String> serviceNames = Arrays.asList(Constants.DBSVC_NAME, Constants.GEODBSVC_NAME);
    private final AtomicBoolean isLeader = new AtomicBoolean(false);
    private List<String> aliveNodes = new ArrayList<String>();
    private List<String> corruptedNodes = new ArrayList<String>();
    private int nodeCount;
    private NamedThreadPoolExecutor recoveryExecutor;
    private NamedThreadPoolExecutor multicastExecutor;
    private boolean waitOnRecoveryTriggering = false;

    private static final long REDEPLOY_MULTICAST_TIMEOUT = 120 * 60 * 1000; // 2 hours

    @Autowired
    private CoordinatorClientExt coordinator;

    @Autowired
    private DbClient dbClient;

    @Autowired
    private LocalRepository localRepository;

    public RecoveryManager() {
    }

    /**
     * Initialize recovery manager
     */
    public void init() {
        if (!isVMwareVapp()) {
            startRecoveryLeaderSelector();
            addRecoveryStatusListener();
        } else {
            log.info("No need to init for node recovery in VMware vApp environment");
        } 
    }

    /**
     * Main loop of Recovery manager. Execute node recovery in case that it is elected as leader.
     */
    @Override
    public void run() {
        while (isLeader.get()) {
            try {
                checkRecoveryStatus();
                checkClusterStatus();
                runNodeRecovery();
            } catch (Exception e) {
                log.warn("Internal error of Recovery manager: ", e.getMessage());
            }
        }
    }

    /**
     * Check the recovery status saved in ZK.
     * a. No recovery required(DONE/FAILED/NULL): nothing to do. simply wait
     * b. In progress(PREPARING/REPAIRING/SYNCING): fail new request if there is one in progress
     * c. Triggering(INIT): current node should take charge of node recovery
     */
    private void checkRecoveryStatus() throws Exception {
        while (true) {
            InterProcessLock lock = null;
            try {
                lock = getRecoveryLock();
                RecoveryStatus status = queryNodeRecoveryStatus();
                if (isRecovering(status)) {
                    log.warn("This is a stale recovery request due to recovery leader change");
                    return;
                } else if (isTriggering(status)) {
                    log.info("The recovery status is triggering so run recovery directly");
                    return;
                }
                setWaitingRecoveryTriggeringFlag(true);
            } catch (Exception e) {
                markRecoveryFailed(RecoveryStatus.ErrorCode.INTERNAL_ERROR);
                throw e;
            } finally {
                releaseLock(lock);
            }
            log.info("Wait to be triggered");
            waitOnRecoveryTriggering();
        }
    }

    private boolean getWaitingRecoveryTriggeringFlag() {
        return waitOnRecoveryTriggering;
    }

    private void setWaitingRecoveryTriggeringFlag(boolean waiting) {
        waitOnRecoveryTriggering = waiting;
        log.info("Setting waiting flag to {}", waiting);
    }

    /**
     * Check if cluster is triggering recovery
     */
    private boolean isTriggering(RecoveryStatus status) {
        return status.getStatus() == RecoveryStatus.Status.INIT;
    }

    /**
     * Check if cluster is recovering
     */
    private boolean isRecovering(RecoveryStatus status) {
        boolean recovering = (status.getStatus() == RecoveryStatus.Status.PREPARING
                || status.getStatus() == RecoveryStatus.Status.REPAIRING
                || status.getStatus() == RecoveryStatus.Status.SYNCING);
        return recovering;
    }

    /**
     * Check if cluster is in minority nodes corrupted scenario
     */
    private void checkClusterStatus() throws Exception {
        initNodeListByCheckDbStatus();
        validateNodesStatus();
    }

    /**
     * Init alive node list and corrupted node list by checking db status and geodb status
     */
    private void initNodeListByCheckDbStatus() throws Exception {
        aliveNodes.clear();
        corruptedNodes.clear();

        for (String serviceName : serviceNames) {
            try (DbManagerOps dbManagerOps = new DbManagerOps(serviceName)) {
                Map<String, Boolean> statusMap = dbManagerOps.getNodeStates();
                for (Map.Entry<String, Boolean> statusEntry : statusMap.entrySet()) {
                    log.info("status map entry: {}-{}", statusEntry.getKey(), statusEntry.getValue());
                    String nodeId = statusEntry.getKey();
                    if (statusEntry.getValue().equals(Boolean.TRUE)) {
                        if (!aliveNodes.contains(nodeId)) {
                            aliveNodes.add(nodeId);
                        }
                    } else {
                        if (!corruptedNodes.contains(nodeId)) {
                            corruptedNodes.add(nodeId);
                        }
                        if (aliveNodes.contains(nodeId)) {
                            aliveNodes.remove(nodeId);
                        }
                    }
                }
            }
        }
        log.info("Alive nodes:{}, corrupted nodes: {}", aliveNodes, corruptedNodes);
    }

    /**
     * Validate cluster is in minority node corrupted scenario
     */
    private void validateNodesStatus() {
        nodeCount = coordinator.getNodeCount();
        if (aliveNodes.size() == nodeCount) {
            markRecoveryCancelled();
            log.warn("All nodes are alive, no need to do recovery");
            throw new IllegalStateException("No need to do recovery");
        } else if (aliveNodes.size() < (nodeCount / 2 + 1)) {
            markRecoveryCancelled();
            log.warn("This procedure doesn't support majority nodes corrupted scenario");
            throw new IllegalStateException("Majority nodes are corrupted");
        }
    }

    /**
     * Start cluster recovery in minority nodes corrupted scenario
     * a. PREPARING: start a multicast thread and then the user do node redeployment
     * b. REPAIRING: run db node repair between the alive nodes to make sure the consistency
     * c. SYNCING: wake the redeployed nodes from hibernate status and do data syncing
     * d. DONE: dbsvc and geodbsvc on all nodes are get started
     * e. FAILED: any error occurred during node recovery
     */
    private synchronized void runNodeRecovery() throws Exception {
        InterProcessLock lock = null;
        try {
            log.info("Node recovery begins");
            lock = getRecoveryLock();

            setRecoveryStatus(RecoveryStatus.Status.PREPARING);
            startMulticastService();

            setRecoveryStatus(RecoveryStatus.Status.REPAIRING);
            runDbRepair();

            setRecoveryStatus(RecoveryStatus.Status.SYNCING);
            waitDbsvcStarted();

            markRecoverySuccessful();
            log.info("Node recovery is done successful");
        } catch (Exception ex) {
            markRecoveryFailed(RecoveryStatus.ErrorCode.INTERNAL_ERROR);
            log.error("Node recovery failed:", ex);
            throw ex;
        } finally {
            releaseLock(lock);
        }
    }

    /**
     * Start multicast service for node redeployment
     * TODO - we are going to remove it after Hyper-V installer is discarded in jedi.
     */
    private void startMulticastService() throws Exception {
        multicastExecutor = new NamedThreadPoolExecutor("Redeploy multicast thread", 1);
        Runnable multicast = new Runnable() {
            @Override
            public void run() {
                try {
                    log.info("Start to multicast cluster configuration for node redeploy.");

                    String version = coordinator.getTargetInfo(RepositoryInfo.class).getCurrentVersion().toString();

                    com.emc.storageos.services.util.Configuration config = PlatformUtils.getLocalConfiguration();
                    config.setScenario(PropertyConstants.REDEPLOY_MODE);
                    config.setAliveNodes(aliveNodes);

                    MulticastUtil.doBroadcast(version, config, REDEPLOY_MULTICAST_TIMEOUT);

                    log.info("Finished multicast cluster configuration for node redeploy.");
                } catch (Exception e) {
                    log.warn("Multicast failed", e);
                }
            }
        };
        multicastExecutor.execute(multicast);
    }

    /**
     * Remove the corrupted nodes and then run db node repair between the alive nodes
     */
    private void runDbRepair() {
        try {
            for (String svcName : serviceNames) {
                try (DbManagerOps dbManagerOps = new DbManagerOps(svcName)) {
                    dbManagerOps.removeNodes(corruptedNodes);
                    dbManagerOps.startNodeRepairAndWaitFinish(true, false);
                }
            }
        } catch (Exception e) {
            log.error("Node repair failed", e);
            markRecoveryFailed(RecoveryStatus.ErrorCode.REPAIR_FAILED);
            throw APIException.internalServerErrors.nodeRepairFailed();
        }
    }

    /**
     * Wait dbsvc and geodbsvc of all nodes get started
     */
    private void waitDbsvcStarted() throws Exception {
        log.info("Wait dbsvc and geodbsvc get started..");
        waitHibernateNodeStarted();
        validateClusterStatus();
    }

    /**
     * Wait dbsvc and geodbsvc on the redeployed nodes get started
     */
    private void waitHibernateNodeStarted() throws Exception {
        long expireTime = System.currentTimeMillis() + RecoveryConstants.RECOVERY_CHECK_TIMEOUT;
        while (true) {
            List<String> hibernateNodes = getHibernateNodes();
            if (hibernateNodes.isEmpty()) {
                log.info("Db node rebuild finished");
                break;
            }
            Thread.sleep(RecoveryConstants.RECOVERY_CHECK_INTERVAL);
            if (System.currentTimeMillis() >= expireTime) {
                log.error("Hibernate nodes({}) can't get started within the stipulated time({})",
                        hibernateNodes, RecoveryConstants.RECOVERY_CHECK_TIMEOUT);
                markRecoveryFailed(RecoveryStatus.ErrorCode.SYNC_FAILED);
                throw APIException.internalServerErrors.nodeRebuildFailed();
            }
        }
    }

    /**
     * Double check dbsvc status on all nodes
     */
    private void validateClusterStatus() throws Exception {
        for (int i = 0; i < RecoveryConstants.RECOVERY_RETRY_COUNT; i++) {
            List<String> unavailableNodes = getUnavailableNodes();
            if (unavailableNodes.isEmpty()) {
                log.info("Dbsvc on all nodes are available");
                break;
            }
            Thread.sleep(RecoveryConstants.RECOVERY_CHECK_INTERVAL);
            log.error("Healthy nodes({}) get unavailable during node recovery", unavailableNodes);
            markRecoveryFailed(RecoveryStatus.ErrorCode.NEW_NODE_FAILURE);
            throw APIException.internalServerErrors.newNodeFailureInNodeRecovery(unavailableNodes.toString());
        }
    }

    /**
     * Get hibernate nodes by check if it exist in cassandra node list
     */
    private List<String> getHibernateNodes() {
        List<String> hibernateNodes = new ArrayList<String>();
        for (int i = 1; i <= nodeCount; i++) {
            String nodeId = "vipr" + i;
            if (aliveNodes.contains(nodeId)) {
                log.debug("No need to check {} which is not a redeployed node", nodeId);
                continue;
            }
            if (isNodeHibernating(nodeId)) {
                hibernateNodes.add(nodeId);
                continue;
            }
            if (!isNodeAvailable(nodeId)) {
                hibernateNodes.add(nodeId);
            }
        }
        log.debug("Get hibernate nodes: {}", hibernateNodes);
        return hibernateNodes;
    }

    private boolean isNodeHibernating(String nodeId) {
        for (String serviceName : serviceNames) {
            try (DbManagerOps dbManagerOps = new DbManagerOps(serviceName)) {
                Map<String, Boolean> statusMap = dbManagerOps.getNodeStates();
                if (!statusMap.keySet().contains(nodeId)) {
                    log.debug("Node({}) is still hibernating", nodeId);
                    return true;
                }
            } catch (Exception e) {
                log.warn("Failed to get hibernate node by checking {}", serviceName);
            }
        }
        log.debug("Node({}) is not hibernated any more", nodeId);
        return false;
    }

    /**
     * Get unavailable nodes by check dbsvc and geodbsvc beacon
     */
    private List<String> getUnavailableNodes() {
        List<String> unavailableNodes = new ArrayList<String>();
        for (int i = 1; i <= nodeCount; i++) {
            String nodeId = "vipr" + i;
            if (!isNodeAvailable(nodeId)) {
                unavailableNodes.add(nodeId);
            }
        }
        log.debug("Get unavailable nodes: {}", unavailableNodes);
        return unavailableNodes;
    }

    private boolean isNodeAvailable(String nodeId) {
        for (String serviceName : serviceNames) {
            List<String> availableNodes = coordinator.getServiceAvailableNodes(serviceName);
            if (!availableNodes.contains(nodeId)) {
                log.debug("Service({}) on node({}) is unavailable");
                return false;
            }
        }
        return true;
    }

    /**
     * Trigger node recovery by update recovery status to 'INIT'
     */
    public void triggerNodeRecovery() {
        InterProcessLock lock = null;
        try {
            lock = getRecoveryLock();

            validatePlatform();
            validateNodeRecoveryStatus();
            validateClusterState();

            RecoveryStatus status = new RecoveryStatus();
            status.setStatus(RecoveryStatus.Status.INIT);
            status.setStartTime(new Date());
            persistNodeRecoveryStatus(status);
        } finally {
            releaseLock(lock);
        }
    }

    /**
     * Check if platform is supported
     */
    private void validatePlatform() {
        if (isVMwareVapp()) {
            log.warn("Platform(vApp) is unsupported for node recovery");
            throw new UnsupportedOperationException("Platform(vApp) is unsupported for node recovery");
        }
    }

    private boolean isVMwareVapp() {
        return PlatformUtils.isVMwareVapp();
    }

    /**
     * Check if have triggered node recovery already
     */
    private void validateNodeRecoveryStatus() {
        RecoveryStatus status = queryNodeRecoveryStatus();
        if (isTriggering(status)) {
            log.warn("Have triggered node recovery already");
            throw new IllegalStateException("Have triggered node recovery already");
        }
    }

    /**
     * Check if cluster need to do node recovery
     */
    private void validateClusterState() {
        ClusterInfo.ClusterState state = coordinator.getCoordinatorClient().getControlNodesState();
        log.info("Current control nodes' state: {}", state);
        if (state == ClusterInfo.ClusterState.STABLE) {
            log.warn("Cluster is stable and no need to do node recovery");
            throw new IllegalStateException("Cluster is stable and no need to do node recovery");
        }
    }

    /**
     * Update node recovery status to ZK
     */
    private void setRecoveryStatus(RecoveryStatus.Status status) {
        if (!isLeader.get()) {
            log.warn("This node is not the recovery leader");
            throw new IllegalStateException("This node is not the recovery leader");
        }
        RecoveryStatus recoveryStatus = queryNodeRecoveryStatus();
        recoveryStatus.setStatus(status);
        persistNodeRecoveryStatus(recoveryStatus);
    }

    /**
     * Update node recovery status to ZK
     */
    private void setRecoveryStatusWithEndTimeMarked(RecoveryStatus.Status status) {
        if (!isLeader.get()) {
            log.warn("This node is not the recovery leader");
            throw new IllegalStateException("This node is not the recovery leader");
        }
        RecoveryStatus recoveryStatus = queryNodeRecoveryStatus();
        recoveryStatus.setStatus(status);
        recoveryStatus.setEndTime(new Date());
        persistNodeRecoveryStatus(recoveryStatus);
    }

    /**
     * Set node recovery status as 'CANCELLED'
     */
    private void markRecoveryCancelled() {
        InterProcessLock lock = null;
        try {
            lock = getRecoveryLock();
            setRecoveryStatusWithEndTimeMarked(RecoveryStatus.Status.CANCELLED);
        } finally {
            releaseLock(lock);
        }
    }

    /**
     * Mark recovery status as successful and set end time
     */
    private void markRecoverySuccessful() {
        setRecoveryStatusWithEndTimeMarked(RecoveryStatus.Status.DONE);
    }

    /**
     * Mark recovery status as failed and set error message and end time
     */
    private void markRecoveryFailed(RecoveryStatus.ErrorCode errorMessage) {
        if (!isLeader.get()) {
            log.warn("This node is not the recovery leader");
            throw new IllegalStateException("This node is not the recovery leader");
        }
        RecoveryStatus recoveryStatus = queryNodeRecoveryStatus();
        if (recoveryStatus.getErrorCode() != null) {
            log.debug("Have already marked.");
            return;
        }
        recoveryStatus.setErrorCode(errorMessage);
        recoveryStatus.setEndTime(new Date());
        recoveryStatus.setStatus(RecoveryStatus.Status.FAILED);
        persistNodeRecoveryStatus(recoveryStatus);

        poweroff(getHibernateNodes());
    }

    /**
     * Persist recovery status to ZK
     */
    private void persistNodeRecoveryStatus(RecoveryStatus status) {
        log.info("Set node recovery status: {}", status);
        if (status == null) {
            return;
        }
        ConfigurationImpl cfg = new ConfigurationImpl();
        cfg.setKind(Constants.NODE_RECOVERY_STATUS);
        cfg.setId(Constants.GLOBAL_ID);

        cfg.setConfig(RecoveryConstants.RECOVERY_STATUS, status.getStatus().toString());
        if (status.getStartTime() != null) {
            cfg.setConfig(RecoveryConstants.RECOVERY_STARTTIME, String.valueOf(status.getStartTime().getTime()));
        }
        if (status.getEndTime() != null) {
            cfg.setConfig(RecoveryConstants.RECOVERY_ENDTIME, String.valueOf(status.getEndTime().getTime()));
        }
        if (status.getErrorCode() != null) {
            cfg.setConfig(RecoveryConstants.RECOVERY_ERRCODE, status.getErrorCode().toString());
        }
        coordinator.getCoordinatorClient().persistServiceConfiguration(cfg);
        log.debug("Persist node recovery status({}) to zk successfully", status);
    }

    /**
     * Query recovery status from ZK
     */
    public RecoveryStatus queryNodeRecoveryStatus() {
        RecoveryStatus status = new RecoveryStatus();
        Configuration cfg = coordinator.getCoordinatorClient().queryConfiguration(Constants.NODE_RECOVERY_STATUS,
                Constants.GLOBAL_ID);
        if (cfg != null) {
            String statusStr = cfg.getConfig(RecoveryConstants.RECOVERY_STATUS);
            status.setStatus(RecoveryStatus.Status.valueOf(statusStr));

            String startTimeStr = cfg.getConfig(RecoveryConstants.RECOVERY_STARTTIME);
            if (startTimeStr != null && startTimeStr.length() > 0) {
                status.setStartTime(new Date(Long.parseLong(startTimeStr)));
            }
            String endTimeStr = cfg.getConfig(RecoveryConstants.RECOVERY_ENDTIME);
            if (endTimeStr != null && endTimeStr.length() > 0) {
                status.setEndTime(new Date(Long.parseLong(endTimeStr)));
            }
            String errorCodeStr = cfg.getConfig(RecoveryConstants.RECOVERY_ERRCODE);
            if (errorCodeStr != null && errorCodeStr.length() > 0) {
                status.setErrorCode(RecoveryStatus.ErrorCode.valueOf(errorCodeStr));
            }
        }
        log.info("Recovery status is: {}", status);
        return status;
    }

    /**
     * Get recovery lock to protect the setting of recovery status
     */
    private InterProcessLock getRecoveryLock() {
        InterProcessLock lock = null;
        log.info("Try to acquire recovery lock");
        try {
            lock = coordinator.getCoordinatorClient().getLock(RecoveryConstants.RECOVERY_LOCK);
            boolean acquired = lock.acquire(RecoveryConstants.RECOVERY_LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new IllegalStateException("Unable to get recovery lock");
            }
        } catch (Exception e) {
            log.error("Get recovery lock failed", e);
            throw APIException.internalServerErrors.getLockFailed();
        }
        log.info("Got recovery lock");
        return lock;
    }

    /**
     * Release recovery lock
     */
    private void releaseLock(InterProcessLock lock) {
        if (lock == null) {
            log.info("The recovery lock is null, no need to release");
            return;
        }
        try {
            lock.release();
            log.info("Release recovery lock successful");
        } catch (Exception ignore) {
            log.warn("Release recovery lock failed", ignore);
        }
    }

    /**
     * Poweroff specific nodes
     * 
     * @param nodeIds a list of node id (e.g. vipr1)
     */
    public void poweroff(List<String> nodeIds) {
        for (String nodeId : nodeIds) {
            try {
                log.info("Try to power off {}", nodeId);
                String svcId = nodeId.replace("vipr", "syssvc-");
                URI nodeEndpoint = coordinator.getNodeEndpointForSvcId(svcId);
                if (nodeEndpoint == null) {
                    continue;
                }
                SysClientFactory.getSysClient(coordinator.getNodeEndpointForSvcId(svcId))
                        .post(SysClientFactory.URI_POWEROFF_NODE, null, null);
                log.info("Power off {} successfully", nodeId);
            } catch (SysClientException e) {
                log.error("Power off node({}) failed", nodeId, e.getMessage());
            }
        }
    }

    /**
     * Poweroff local node
     */
    public void poweroff() {
        localRepository.poweroff();
    }

    /**
     * Register recovery status listener to monitor the status's change
     */
    private void addRecoveryStatusListener() {
        try {
            coordinator.getCoordinatorClient().addNodeListener(new RecoveryStatusListener());
        } catch (Exception e) {
            log.error("Fail to add recovery status listener", e);
            throw APIException.internalServerErrors.addListenerFailed();
        }
    }

    /**
     * The listener class is to listen the recovery status node change.
     */
    private class RecoveryStatusListener implements NodeListener {

        @Override
        public String getPath() {
            String path = String.format("%1$s/%2$s/%3$s", ZkPath.CONFIG, Constants.NODE_RECOVERY_STATUS,
                    Constants.GLOBAL_ID);
            return path;
        }

        /**
         * Called when a change of recovery status has occurred
         */
        @Override
        public void nodeChanged() {
            wakeupRecoveryThread();
        }

        /**
         * Called when connection status changed
         */
        @Override
        public void connectionStateChanged(State state) {
        }
    }

    private synchronized void waitOnRecoveryTriggering() throws InterruptedException {
        if (getWaitingRecoveryTriggeringFlag()) {
            this.wait();
        }
    }

    private synchronized void wakeupRecoveryThread() {
        if (getWaitingRecoveryTriggeringFlag()) {
            log.info("Try to notify the semaphore");
            this.notifyAll();
            setWaitingRecoveryTriggeringFlag(false);
        }
    }

    /**
     * Use leader selector to make sure only one node(leader node) start recovery manager
     */
    private void startRecoveryLeaderSelector() {
        while (!coordinator.getCoordinatorClient().isConnected()) {
            log.info("Waiting for connecting to zookeeper");
            try {
                Thread.sleep(RecoveryConstants.RECOVERY_CONNECT_INTERVAL);
            } catch (InterruptedException e) {
                log.warn("Exception while sleeping, ignore", e);
            }
        }
        LeaderSelector leaderSelector = coordinator.getCoordinatorClient().getLeaderSelector(
                RecoveryConstants.RECOVERY_LEADER_PATH,
                new RecoveryLeaderSelectorListener());
        leaderSelector.autoRequeue();
        leaderSelector.start();
    }

    /**
     * The listener class is to listen the leader node change.
     */
    private class RecoveryLeaderSelectorListener extends LeaderSelectorListenerImpl {
        @Override
        protected void startLeadership() throws Exception {
            log.info("Select as leader, wait to start recovery manager");
            isLeader.set(true);
            start();
        }

        @Override
        protected void stopLeadership() {
            log.info("Give up leader, try to stop recovery manager");
            isLeader.set(false);
            stop();
        }
    }

    private void start() {
        recoveryExecutor = new NamedThreadPoolExecutor("Recovery manager", 1);
        recoveryExecutor.execute(this);
    }

    private void stop() {
        recoveryExecutor.shutdownNow();
        try {
            while (!recoveryExecutor.awaitTermination(RecoveryConstants.THREAD_CHECK_INTERVAL, TimeUnit.SECONDS)) {
                log.warn("Waiting recovery thread pool to shutdown for another {} seconds",
                        RecoveryConstants.THREAD_CHECK_INTERVAL);
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting to shutdown recovery thread pool", e);
        }
    }
}
