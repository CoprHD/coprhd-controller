/*
 * Copyright (c) 2012-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.services.util.Waiter;
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

    protected CoordinatorClientExt coordinator;
    protected LocalRepository localRepository;

    protected volatile boolean doRun = true;

    protected int nodeCount;

    protected final String upgradeLockId = DISTRIBUTED_UPGRADE_LOCK;
    protected final String propertyLockId = DISTRIBUTED_PROPERTY_LOCK;
    protected final String vdcLockId = DISTRIBUTED_VDC_LOCK;

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
