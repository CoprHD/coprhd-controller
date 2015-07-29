/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.zkutils;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.coordinator.client.model.Constants;

/**
 * Handle upgrade lock to control upgrade and other process
 */
public class LockCmdHandler {
    private static final Logger log = LoggerFactory.getLogger(LockCmdHandler.class);
    private static final String ZKUTI_CONF = "/zkutils-conf.xml";
    private static final String COORDINATOR_BEAN = "coordinatorclientext";

    private static ApplicationContext ctx;
    private CoordinatorClientExt coordinatorClientExt;
    // syssvc id, like syssvc-1, syssvc-2
    private String mysvcId;
    private String upgradeLockId;

    public LockCmdHandler() {
        // Suppress Sonar violation of Lazy initialization of static fields should be synchronized
        // This constructor used in Main, and only called in main thread, so it's safe to initialize ctx here
        ctx = new ClassPathXmlApplicationContext(ZKUTI_CONF); // NOSONAR("squid:S2444")
        coordinatorClientExt = (CoordinatorClientExt) ctx.getBean(COORDINATOR_BEAN);
        mysvcId = coordinatorClientExt.getMySvcId();
        upgradeLockId = Constants.DISTRIBUTED_UPGRADE_LOCK;
        log.info("svcId of this node: {}", mysvcId);
        log.info("upgradeLockId: {}", upgradeLockId);
    }

    /**
     * Lock it before upgrade process starts. We grab two lock for different
     * nodes to make sure suspend the upgrading let other node to get the
     * upgrade lock and this node get the target lock
     */
    public void aquireUpgradeLocks() {
        log.info("Start to lock the system to prevent upgrading...");
        String leader = chooseOtherNodeSvcId();
        // get upgrade lock
        boolean flag = coordinatorClientExt.getPersistentLock(leader, upgradeLockId);
        log.info("Get upgrade lock for {}, {}", leader, flag);
        if (flag) {
            // get target lock
            if (getTargetInfoLock()) {
                System.out.println("Succeed! Upgrade is Locking!");
            }
        }
        else {
            log.error("Fail to get upgrade lock!");
            throw new RuntimeException("Some node grabbed the lock already, "
                    + "please try hold on the upgrade process method or release all first.");
        }
    }

    /**
     * Hold on the upgrade process
     */
    public void aquireUpgradeLockByLoop() {
        System.out.println("Start busy loop to get the lock...");
        boolean flag = false;
        String leader;
        while (flag != true) {
            // 1st, check someone hold the upgrade lock, means it is in upgrade process
            while ((leader = coordinatorClientExt.getUpgradeLockOwner(upgradeLockId)) != null) {
                // because the upgrade process will release the lock to let other nodes grab
                // 2nd, manually let leader get the lock again at the time it release the lock.
                while ((flag = coordinatorClientExt.getPersistentLock(leader, upgradeLockId)) != true) {
                    leader = coordinatorClientExt.getUpgradeLockOwner(upgradeLockId);
                }
                System.out.println("Hold on Succeed!");
                log.info("The {} get the lock, {}", leader, flag);
                break;
            }
        }
    }

    /**
     * Release all locks for upgrading continuously
     */
    public void releaseAllLocks() {
        releaseTargetInfoLock();
        if (releaseUpgradeLock()) {
            System.out.println("Release all lock succeed!");
        } else {
            System.out.println("Relase Fail, Please see the log.");
        }
    }

    /**
     * Release upgrade lock
     * 
     * @return true release successfully, otherwise false
     */
    public boolean releaseUpgradeLock() {
        log.info("Strat releasing upgrade Lock ...");
        boolean flag = false;
        String leader = coordinatorClientExt.getUpgradeLockOwner(upgradeLockId);
        if (leader != null) {
            log.info("Now upgrade lock belongs to: {}", leader);
        }
        try {
            flag = coordinatorClientExt.releasePersistentLock(upgradeLockId);
        } catch (Exception e) {
            log.error("Fail to release upgrade lock! {}", e);
        }
        log.info("Release upgrade lock: {}", flag);
        return flag;
    }

    /**
     * Get target info lock for this node
     * 
     * @return true get successfully, otherwise false
     */
    public boolean getTargetInfoLock() {
        log.info("Start getting target version lock");
        boolean flag = false;
        flag = coordinatorClientExt.getTargetInfoLock();
        log.info("Get target lock for {}, {}", mysvcId, flag);
        return flag;
    }

    /**
     * Release the non-persistent target version lock
     */
    public void releaseTargetInfoLock() {
        log.info("Start releasing target version lock");
        coordinatorClientExt.releaseTargetVersionLock();
        log.info("Release the target version lock.");
    }

    /**
     * The method to identify and return the node which is currently holding the
     * persistent upgrade lock
     * 
     * @return NodeHandle - for node which holds the lock, null - If no node
     *         holds the lock
     */
    public String getUpgradeLockOwner() {
        String leader = coordinatorClientExt.getUpgradeLockOwner(upgradeLockId);
        System.out.println(String.format("Lock belongs to leader: %s.", leader));
        return leader;
    }

    /**
     * Get another node svc-id for getting upgrade lock
     * 
     * @return another node svcid
     */
    private String chooseOtherNodeSvcId() {
        List<String> nodes = coordinatorClientExt.getAllNodes();
        for (String node : nodes) {
            if (!node.equals(mysvcId)) {
                log.info("Other node svc id is: {}", node);
                return node;
            }
        }
        log.info("No other node, return self");
        return mysvcId;
    }

}
