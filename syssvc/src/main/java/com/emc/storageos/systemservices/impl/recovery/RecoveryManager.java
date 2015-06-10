/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices.impl.recovery;

import java.net.URI;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.leader.LeaderSelector;

import com.emc.vipr.model.sys.recovery.RecoveryStatus;
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
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.exceptions.SysClientException;
import com.emc.storageos.services.util.NamedThreadPoolExecutor;
import com.emc.storageos.services.util.PlatformUtils;
import com.emc.storageos.management.jmx.recovery.DbManagerOps;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

import com.emc.storageos.services.util.InstallerUtil;
import com.emc.storageos.services.util.InstallerConstants;
import com.emc.storageos.services.util.InstallerOperation;

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
        startRecoveryLeaderSelector();
        addRecoveryStatusListener();
    }

    /**
     * Main loop of Recovery manager. Execute node recovery in case that it is elected as leader. 
     */
    @Override
    public void run() {
        while(isLeader.get()) {
            try {
                checkRecoveryStatus();
                checkPlatform();
                checkClusterStatus();
                runNodeRecovery();
            } catch (Exception e) {
                log.warn("Internal error of Recovery manager", e);
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
        if (status == null) {
            return false;
        }
        return status.getStatus() == RecoveryStatus.Status.INIT;
    }

    /**
     * Check if cluster is recovering
     */
    private boolean isRecovering(RecoveryStatus status) {
        if (status == null) {
            return false;
        }
        boolean recovering =  (status.getStatus() == RecoveryStatus.Status.PREPARING
                || status.getStatus() == RecoveryStatus.Status.REPAIRING
                || status.getStatus() == RecoveryStatus.Status.SYNCING);
        return recovering;
    }

    /**
     * Check if platform is supported
     */
    private void checkPlatform() {
        if (PlatformUtils.isVMwareVapp()){
            log.info("Platform(VApp) is unsupported for node recovery");
            clearRecoveryStatus();
            throw new UnsupportedOperationException("VApp is unsupported for node recovery");
        }
    }

    /**
     * Check if cluster is in minority nodes corrupted scenario
     */
    private void checkClusterStatus() throws Exception {
        initNodeListByCheckDbStatus();
        validateClusterStatus();
    }

    /**
     * Init alive node list and corrupted node list by checking db status and geodb status
     */
    private void initNodeListByCheckDbStatus() throws Exception {
        aliveNodes.clear();
        corruptedNodes.clear();

        Iterator serviceIter = serviceNames.iterator();
        while (serviceIter.hasNext()) {
            DbManagerOps dbManagerOps = new DbManagerOps((String) serviceIter.next());
            Map<String, Boolean> statusMap = dbManagerOps.getNodeStates();
            for (Map.Entry<String, Boolean> statusEntry : statusMap.entrySet()) {
                log.info("status map entry: {}-{}", statusEntry.getKey(), statusEntry.getValue());
                if (statusEntry.getValue().equals(Boolean.TRUE)) {
                    if (!aliveNodes.contains(statusEntry.getKey()))
                        aliveNodes.add(statusEntry.getKey());
                } else {
                    if (!corruptedNodes.contains(statusEntry.getKey()))
                        corruptedNodes.add(statusEntry.getKey());
                }
            }
        }
        log.info("Alive nodes:{}, corrupted nodes: {}", aliveNodes.toString(), corruptedNodes.toString());
    }

    /**
     * Validate cluster is in minority node corrupted scenario
     */
    private void validateClusterStatus() {
        nodeCount = coordinator.getNodeCount();
        if (aliveNodes.size() == nodeCount) {
            clearRecoveryStatus();
            log.warn("All nodes are alive, no need to do recovery");
            throw new IllegalStateException("No need to do recovery");
        } else if (aliveNodes.size() < (nodeCount/2 + 1)) {
            clearRecoveryStatus();
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
            log.error("Node recovery failed:", ex.getMessage());
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

                    com.emc.storageos.services.data.Configuration config = InstallerOperation.getLocalConfiguration();
                    config.setScenario(InstallerConstants.REDEPLOY_MODE);
                    config.setAliveNodes(aliveNodes);

                    InstallerUtil.doBroadcast(version, config, InstallerConstants.REDEPLOY_MULTICAST_TIMEOUT);

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
            Iterator serviceIter = serviceNames.iterator();
            while (serviceIter.hasNext()) {
                DbManagerOps dbManagerOps = new DbManagerOps((String)serviceIter.next());
                dbManagerOps.removeNodes(corruptedNodes);
                dbManagerOps.startNodeRepairAndWaitFinish(true, false);
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
        long expireTime = System.currentTimeMillis() + RecoveryConstants.RECOVERY_CHECK_TIMEOUT;

        while (true) {
            List<String> nodes = getHibernateNodes();
            if (nodes.size() == 0) {
                break;
            }
            Thread.sleep(RecoveryConstants.RECOVERY_CHECK_INTERVAL);
            if (System.currentTimeMillis() >= expireTime) {
                log.error("Dbsvc and geodbsvc could not get started within the stipulated time({})",
                        RecoveryConstants.RECOVERY_CHECK_TIMEOUT);
                markRecoveryFailed(RecoveryStatus.ErrorCode.SYNC_FAILED);
                throw APIException.internalServerErrors.nodeRebuildFailed();
            }
        }
        log.info("Db node rebuild finished");
    }

    /**
     * Get the nodes list of specific service have started
     */
    private List<String> getAvailableNodes(String serviceName) {
        List<String> availableNodes = new ArrayList<String>();
        try {
            List<Service> services = coordinator.locateAllServices(
                    serviceName, dbClient.getSchemaVersion(), null, null);
            for (Service svc : services) {
                final String svcId = svc.getId();
                if (svcId != null) {
                    String nodeId = "vipr" + svcId.substring(svcId.lastIndexOf("-") + 1);
                    availableNodes.add(nodeId);
                }
            }
        } catch (Exception ex) {
            log.warn("Check service({}) beacon error", serviceName, ex);
        }
        log.debug("Get available nodes by check {}: {}", serviceName, availableNodes.toString());
        return availableNodes;
    }

    /**
     * Get hibernate nodes by check dbsvc and geodbsvc beacon
     */
    private List<String> getHibernateNodes() {
        List<String> hibernateNodes = new ArrayList<String>();
        List<String> dbAvailableNodes = getAvailableNodes(serviceNames.get(0));
        List<String> geodbAvailableNodes = getAvailableNodes(serviceNames.get(1));
        for (int i = 1; i <= nodeCount; i++) {
            String nodeId = "vipr"+i;
            if (!dbAvailableNodes.contains(nodeId) || !geodbAvailableNodes.contains(nodeId)) {
                hibernateNodes.add(nodeId);
            }
        }
        log.debug("Get hibernate nodes: {}", hibernateNodes.toString());
        return hibernateNodes;
    }

    /**
     * Trigger node recovery by update recovery status to 'INIT'
     */
    public void triggerNodeRecovery() {
        InterProcessLock lock = null;
        try {
            lock = getRecoveryLock();
            RecoveryStatus status = queryNodeRecoveryStatus();
            if (isTriggering(status)) {
                log.warn("Have triggered node recovery already");
                throw new IllegalStateException("Have triggered node recovery already");
            }
            status = new RecoveryStatus();
            status.setStatus(RecoveryStatus.Status.INIT);
            status.setStartTime(new Date());
            persistNodeRecoveryStatus(status);
        } finally {
            releaseLock(lock);
        }
    }

    /**
     * Clear node recovery status in ZK
     */
    private  void clearRecoveryStatus() {
        InterProcessLock lock = null;
        try {
            lock = getRecoveryLock();
            if (!isLeader.get()) {
                throw new IllegalStateException("This node is not the recovery leader");
            }
            ConfigurationImpl cfg = new ConfigurationImpl();
            cfg.setKind(Constants.NODE_RECOVERY_STATUS);
            cfg.setId(Constants.GLOBAL_ID);
            coordinator.getCoordinatorClient().removeServiceConfiguration(cfg);
        } finally {
            releaseLock(lock);
        }
    }

    /**
     * Update node recovery status to ZK
     */
    private  void setRecoveryStatus(RecoveryStatus.Status status) {
        if (!isLeader.get()) {
            log.warn("This node is not the recovery leader");
            throw new IllegalStateException("This node is not the recovery leader");
        }
        RecoveryStatus recoveryStatus = queryNodeRecoveryStatus();
        if (recoveryStatus == null) {
            recoveryStatus = new RecoveryStatus();
        }
        recoveryStatus.setStatus(status);
        persistNodeRecoveryStatus(recoveryStatus);
    }

    /**
     * Mark recovery status as successful and set end time
     */
    private void markRecoverySuccessful() {
        if (!isLeader.get()) {
            log.warn("This node is not the recovery leader");
            throw new IllegalStateException("This node is not the recovery leader");
        }
        RecoveryStatus recoveryStatus = queryNodeRecoveryStatus();
        if (recoveryStatus == null) {
            recoveryStatus = new RecoveryStatus();
        }
        recoveryStatus.setStatus(RecoveryStatus.Status.DONE);
        recoveryStatus.setEndTime(new Date());
        persistNodeRecoveryStatus(recoveryStatus);
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
        if (recoveryStatus != null && recoveryStatus.getErrorCode() != null) {
            log.debug("Have already marked.");
            return;
        }
        if (recoveryStatus == null) {
            recoveryStatus = new RecoveryStatus();
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
        log.info("Set node recovery status: {}" , status);
        if (status == null) {
            return;
        }
        ConfigurationImpl cfg = new ConfigurationImpl();
        cfg.setKind(Constants.NODE_RECOVERY_STATUS);
        cfg.setId(Constants.GLOBAL_ID);
        if (status.getStatus() != null) {
            cfg.setConfig(RecoveryConstants.RECOVERY_STATUS, status.getStatus().toString());
        }
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
        log.debug("Persist node recovery status({}) to zk successfully", status.toString());
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
            if (statusStr != null && statusStr.length() > 0) {
                status.setStatus(RecoveryStatus.Status.valueOf(statusStr));
            }
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
        log.info("Recovery status is: {}", status.toString());
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
            log.error("Get recovery lock failed",  e);
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
            log.info("The recovery lock is null");
            return;
        }
        try {
            lock.release();
            log.info("Release recovery lock");
        } catch (Exception ignore) {
            log.warn("Release lock failed, {}", ignore.getMessage());
        }
    }

    /**
     * Poweroff specific nodes
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
    private void startRecoveryLeaderSelector(){
        while(!coordinator.getCoordinatorClient().isConnected()){
            log.info("Waiting for connecting to zookeeper");
            try {
                Thread.sleep(RecoveryConstants.RECOVERY_CONNECT_INTERVAL);
            } catch (InterruptedException e) {
                log.warn("Exception while sleeping, ignore",e);
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
            log.info("Give up leader, stop recovery manager");
            isLeader.set(false);
            stop();
        }
    }

    private void start() {
        recoveryExecutor = new NamedThreadPoolExecutor("Recovery manager", 1);
        recoveryExecutor.execute(this);
   }

    private void stop() {
        recoveryExecutor.shutdown();
        wakeupRecoveryThread();
    }

    /**
     * Get node repair status(have synthesized db repair status and geodb repair status)
     */
    public DbRepairStatus getDbRepairStatus() throws Exception {
        DbRepairStatus repairStatus = new DbRepairStatus();
        DbRepairStatus localDbState = queryDbRepairStatus(serviceNames.get(0));
        DbRepairStatus geodbState = queryDbRepairStatus(serviceNames.get(1));
        log.info("Query repair status of dbsvc({}) and geodbsvc({}) successfully",
                (localDbState == null) ? localDbState : localDbState.toString(),
                (geodbState == null) ? geodbState : geodbState.toString());

        if (localDbState != null && geodbState != null) {
            DbRepairStatus.Status status;
            if (localDbState.getStatus() == DbRepairStatus.Status.IN_PROGRESS || geodbState.getStatus() == DbRepairStatus.Status.IN_PROGRESS) {
                status = DbRepairStatus.Status.IN_PROGRESS;
            } else if (localDbState.getStatus() == DbRepairStatus.Status.FAILED || geodbState.getStatus() == DbRepairStatus.Status.FAILED) {
                status = DbRepairStatus.Status.FAILED;
            } else {
                status = DbRepairStatus.Status.SUCCESS;
            }
            repairStatus.setStatus(status);
            repairStatus.setProgress((localDbState.getProgress() + geodbState.getProgress())/2);
            repairStatus.setStartTime(localDbState.getStartTime());
            repairStatus.setLastCompletionTime(geodbState.getLastCompletionTime());
        } else if (localDbState != null || geodbState != null) {
            DbRepairStatus repairState = (localDbState != null) ? localDbState : geodbState;
            repairStatus.setStatus(repairState.getStatus());
            repairStatus.setProgress(repairState.getProgress());
            repairStatus.setStartTime(repairState.getStartTime());
            repairStatus.setLastCompletionTime(repairState.getLastCompletionTime());
        } else {
            repairStatus.setStatus(DbRepairStatus.Status.NOT_STARTED);
        }

        log.info("Repair status is: {}", repairStatus.toString());
        return repairStatus;
    }

    /**
     * Query repair status of dbsvc or geodbsvc from DB
     */
    private DbRepairStatus queryDbRepairStatus(String svcName) throws Exception {
        int progress = -1;
        DbRepairStatus.Status status = null;
        Date startTime = null;
        Date endTime = null;

        log.info("Try to get repair status of {}", svcName);
        DbManagerOps dbManagerOps = new DbManagerOps(svcName);
        DbRepairStatus repairState = dbManagerOps.getLastRepairStatus(true);
        if (repairState != null) {
            log.info("Current repair status of {} is: {}", svcName, repairState.toString());
            progress = repairState.getProgress();
            status = repairState.getStatus();
            startTime = repairState.getStartTime();
            endTime = repairState.getLastCompletionTime();
        }
        if (endTime != null) {
            return repairState;
        }

        repairState = dbManagerOps.getLastSucceededRepairStatus(true);
        if (repairState != null) {
            log.info("Last successful repair status of {} is: {}", svcName, repairState.toString());
            progress = (progress == -1) ? repairState.getProgress() : progress;
            status = (status == null) ? repairState.getStatus() : status;
            startTime = (startTime == null) ? repairState.getStartTime() : startTime;
            endTime = (endTime == null) ? repairState.getLastCompletionTime(): endTime;
        }

        if (status != null) {
            return new DbRepairStatus(status, startTime, endTime, progress);
        }
        return null;
    }
}

