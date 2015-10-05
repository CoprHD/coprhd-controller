/*
 * Copyright (c) 2012-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.PowerOffState;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.services.util.Waiter;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.exceptions.SysClientException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;

import static com.emc.storageos.coordinator.client.model.Constants.*;

/**
 * Base class for UpgradeManager and PropertyManager
 * It contains a long-sleeping Waiter thread that can be waken up on demand.
 */
public abstract class AbstractManager implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(AbstractManager.class);

    protected boolean shortSleep = false;

    protected final Waiter waiter = new Waiter();
    // bean properties
    protected long loopInterval;
    protected long retryInterval;
    
    // bean properties
    private long powerOffStateChangeTimeout;
    private long powerOffStateProbeInterval;
    
    protected CoordinatorClientExt coordinator;
    protected LocalRepository localRepository;

    protected volatile boolean doRun = true;

    protected int nodeCount;

    protected final String upgradeLockId = DISTRIBUTED_UPGRADE_LOCK;
    protected final String propertyLockId = DISTRIBUTED_PROPERTY_LOCK;
    protected final String vdcLockId = DISTRIBUTED_VDC_LOCK;
    
    private final static int TIME_LIMIT_FOR_INITIATING_POWEROFF = 60000;
    private static final int SLEEP_MS = 100;
    
    private HashSet<String> poweroffAgreementsKeeper = new HashSet<>();

    public HashSet<String> getPoweroffAgreementsKeeper() {
        return poweroffAgreementsKeeper;
    }

    public void setPowerOffStateChangeTimeout(long powerOffStateChangeTimeout) {
        this.powerOffStateChangeTimeout = powerOffStateChangeTimeout;
    }

    public void setPowerOffStateProbeInterval(long powerOffStateProbeInterval) {
        this.powerOffStateProbeInterval = powerOffStateProbeInterval;
    }
    
    public void setCoordinator(CoordinatorClientExt coordinator) {
        this.coordinator = coordinator;
    }

    public CoordinatorClientExt getCoordinator() {
        return coordinator;
    }

    public void setLocalRepository(final LocalRepository localRepository) {
        this.localRepository = localRepository;
    }

    public void setLoopInterval(long interval) {
        loopInterval = interval;
    }

    public void setRetryInterval(long interval) {
        retryInterval = interval;
    }

    public void setNodeCount(int nodeCount) {
        this.nodeCount = nodeCount;
    }

    abstract protected URI getWakeUpUrl();

    public void wakeupOtherNodes() {
        final List<String> svcIds = coordinator.getAllNodes();
        final String mySvcId = coordinator.getMySvcId();

        for (String svcId : svcIds) {
            if (!svcId.equals(mySvcId)) {
                try {
                    SysClientFactory.getSysClient(coordinator.getNodeEndpointForSvcId(svcId))
                            .post(getWakeUpUrl(), null, null);
                } catch (SysClientException e) {
                    log.error("Error waking up node: {} Cause: {}", svcId, e.getMessage());
                }
            }
        }
    }

    public void wakeupAllNodes() {
        wakeupOtherNodes();
        wakeup();
    }

    @Override
    public void run() {
        try {
            innerRun();
        } catch (Exception e) {
            log.error("Unexpected exception in {}", getClass().getSimpleName(), e);
            System.exit(1);
        }
    }

    protected abstract void innerRun();

    /**
     * Check if node_count/2 + 1 dbsvc instances are active on other nodes in the cluster
     * so that if the current node is powered off, a quorum will still be maintained.
     * 
     * @return true if a quorum can be maintained, false otherwise
     */
    protected boolean isQuorumMaintained() {
        if (nodeCount == 1) {
            log.info("There's no way to maintain quorum on single node deployments. Proceed anyway.");
            return true;
        }

        int quorumNodeCnt = nodeCount / 2 + 1;

        CoordinatorClient coordinatorClient = coordinator.getCoordinatorClient();

        List<Service> allActiveDbsvcs = coordinatorClient.locateAllSvcsAllVers(Constants.DBSVC_NAME);
        List<String> otherActiveDbsvcIds = new ArrayList<>();

        String mySvcId = coordinator.getMySvcId();
        String localDbSvcId = "db" + mySvcId.substring(mySvcId.lastIndexOf("-"));
        for (Service activeDbsvc : allActiveDbsvcs) {
            if (!localDbSvcId.equals(activeDbsvc.getId())) {
                otherActiveDbsvcIds.add(activeDbsvc.getId());
            }
        }
        log.info("List of active dbsvc instances on other nodes: {}, expect {} instances to maintain quorum",
                otherActiveDbsvcIds, quorumNodeCnt);

        boolean isMaintained = otherActiveDbsvcIds.size() >= quorumNodeCnt;
        if (!isMaintained) {
            log.info("quorum would lost if reboot the current node. Retrying...");
        }
        return isMaintained;
    }

    /**
     * Check if all dbsvc instances are active in the cluster
     * currently it's only being used before adjusting db token number but it might as well be used elsewhere.
     * 
     * @return true if all dbsvc are active, false otherwise
     */
    protected boolean areAllDbsvcActive() {
        CoordinatorClient coordinatorClient = coordinator.getCoordinatorClient();

        List<Service> activeDbsvcs = coordinatorClient.locateAllSvcsAllVers(Constants.DBSVC_NAME);
        List<String> activeDbsvcIds = new ArrayList<>(activeDbsvcs.size());

        for (Service activeDbsvc : activeDbsvcs) {
            activeDbsvcIds.add(activeDbsvc.getId());
        }
        log.info("List of active dbsvc instances in the cluster: {}, expect {} instances",
                activeDbsvcIds, nodeCount);

        boolean allActive = activeDbsvcs.size() == nodeCount;
        if (!allActive) {
            log.info("not all dbsvc instances are active. Retrying...");
        }
        return allActive;
    }

    /**
     * After executing the reboot set doRun to false so that
     * the main loop will exit
     */
    protected void reboot() {
        localRepository.reboot();
        doRun = false;
    }

    protected void reachAgreementOnPoweroff(boolean forceSet) {
        if (checkAllNodesAgreeToPowerOff(forceSet) && initiatePoweroff(forceSet)) {
            resetTargetPowerOffState();
        } else {
            log.warn("Failed to reach agreement among all the nodes. Proceed with best-effort poweroff");
            initiatePoweroff(true);
            resetTargetPowerOffState();
        }
    }
    
    public boolean initiatePoweroff(boolean forceSet) {
        final List<String> svcIds = coordinator.getAllNodes();
        final String mySvcId = coordinator.getMySvcId();
        svcIds.remove(mySvcId);
        Set<String> controlerSyssvcIdSet = new HashSet<String>();
        for (String svcId : svcIds) {
            if (svcId.matches("syssvc-\\d")) {
                controlerSyssvcIdSet.add(svcId);
            }
        }

        log.info("Tell other node it's ready to power off");

        for (String svcId : controlerSyssvcIdSet) {
            try {
                SysClientFactory.getSysClient(coordinator.getNodeEndpointForSvcId(svcId))
                        .post(URI.create(SysClientFactory.URI_SEND_POWEROFF_AGREEMENT.getPath() + "?sender=" + mySvcId), null, null);
            } catch (SysClientException e) {
                throw APIException.internalServerErrors.poweroffError(svcId, e);
            }
        }
        long endTime = System.currentTimeMillis() + TIME_LIMIT_FOR_INITIATING_POWEROFF;
        while (true) {
            if (System.currentTimeMillis() > endTime) {
                if (forceSet) {
                    return true;
                } else {
                    log.error("Timeout. initiating poweroff failed.");
                    log.info("The received agreements are: " + this.getPoweroffAgreementsKeeper().toString());
                    return false;
                }
            }
            if (poweroffAgreementsKeeper.containsAll(controlerSyssvcIdSet)) {
                return true;
            } else {
                log.debug("Sleep and wait for poweroff agreements for other nodes");
                sleep(SLEEP_MS);
            }
        }
    }

    /**
     * Check all nodes agree to power off
     * Work flow:
     * Each node publishes NOTICED, then wait to see if all other nodes got the NOTICED.
     * If true, continue to publish ACKNOWLEDGED; if false, return false immediately. Poweroff will fail.
     * Same for ACKNOWLEDGED.
     * After a node see others have the ACKNOWLEDGED published, it can power off.
     * 
     * If we let the node which first succeeded to see all ACKNOWLEDGED to power off first,
     * other nodes may fail to see the ACKNOWLEDGED signal since the 1st node is shutting down.
     * So we defined an extra STATE.POWEROFF state, which won't check the count of control nodes.
     * Nodes in POWEROFF state are free to poweroff.
     * 
     * @param forceSet
     * @return true if all node agree to poweroff; false otherwise
     */
    protected boolean checkAllNodesAgreeToPowerOff(boolean forceSet) {
        while (true) {
            try {
                // Send NOTICED signal and verify
                publishNodePowerOffState(PowerOffState.State.NOTICED);
                poweroffAgreementsKeeper = new HashSet<>();
                if (!waitClusterPowerOffStateNotLessThan(PowerOffState.State.NOTICED, !forceSet)) {
                    log.error("Failed to get {} signal from all other nodes", PowerOffState.State.NOTICED);
                    return false;
                }
                // Send ACKNOWLEDGED signal and verify
                publishNodePowerOffState(PowerOffState.State.ACKNOWLEDGED);
                if (!waitClusterPowerOffStateNotLessThan(PowerOffState.State.ACKNOWLEDGED, !forceSet)) {
                    log.error("Failed to get {} signal from all other nodes", PowerOffState.State.ACKNOWLEDGED);
                    return false;
                }

                // Send POWEROFF signal and verify
                publishNodePowerOffState(PowerOffState.State.POWEROFF);
                if (!waitClusterPowerOffStateNotLessThan(PowerOffState.State.POWEROFF, !forceSet)) {
                    log.error("Failed to get {} signal from all other nodes", PowerOffState.State.POWEROFF);
                    return false;
                }

                return true;
            } catch (Exception e) {
                log.error("Step2: checkAllNodesAgreeToPowerOff failed: {} ", e);
            }
        }
    }

    /**
     * Reset target power off state back to NONE
     */
    protected void resetTargetPowerOffState() {
        poweroffAgreementsKeeper = new HashSet<String>();
        while (true) {
            try {
                if (coordinator.isControlNode()) {
                    try {
                        coordinator.setTargetInfo(new PowerOffState(PowerOffState.State.NONE), false);
                        log.info("Step2: Target poweroff state change to: {}", PowerOffState.State.NONE);
                    } catch (CoordinatorClientException e) {
                        log.info("Step2: Wait another control node to set target poweroff state");
                        retrySleep();
                    }
                } else {
                    log.info("Wait control node to set target poweroff state");
                    retrySleep();
                }

                // exit only when target poweroff state is NONE
                if (coordinator.getTargetInfo(PowerOffState.class).getPowerOffState() == PowerOffState.State.NONE) {
                    break;
                }
            } catch (Exception e) {
                retrySleep();
                log.info("reset cluster poweroff state retrying. {}", e);
            }
        }
    }

    /**
     * Publish node power off state
     * 
     * @param toState
     * @throws com.emc.storageos.systemservices.exceptions.CoordinatorClientException
     */
    protected void publishNodePowerOffState(PowerOffState.State toState) throws CoordinatorClientException {
        log.info("Send {} signal", toState);
        coordinator.setNodeSessionScopeInfo(new PowerOffState(toState));
    }

    /**
     * Wait cluster power off state change to a state not less than specified state
     * 
     * @param state
     * @param checkNumOfControlNodes
     * @return true if all nodes' poweroff state are equal to specified state
     */
    private boolean waitClusterPowerOffStateNotLessThan(PowerOffState.State state, boolean checkNumOfControlNodes) {
        long expireTime = System.currentTimeMillis() + powerOffStateChangeTimeout;
        while (true) {
            if (coordinator.verifyNodesPowerOffStateNotBefore(state, checkNumOfControlNodes)) {
                return true;
            }

            sleep(powerOffStateProbeInterval);
            if (System.currentTimeMillis() >= expireTime) {
                return false;
            }
        }
    }
    
    protected void retrySleep() {
        sleep(retryInterval);
    }

    protected void longSleep() {
        if (shortSleep) {
            retrySleep();
        } else {
            sleep(loopInterval);
        }
    }

    protected void sleep(final long ms) {
        waiter.sleep(ms);
    }

    public void wakeup() {
        waiter.wakeup();
    }

    public void stop() {
        doRun = false;
    }
}
