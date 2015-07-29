/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.emc.storageos.coordinator.client.model.RepositoryInfo;
import com.emc.storageos.coordinator.client.model.PropertyInfoExt;

import com.emc.storageos.systemservices.exceptions.SyssvcInternalException;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.impl.SysSvcImpl;

public class UpgradeCoordinatorClientTest {
    private static final Logger _logger = LoggerFactory
            .getLogger(UpgradeCoordinatorClientTest.class);
    private static final String SERVICE_BEAN = "syssvcserver";
    private static final String COORDINATOR_BEAN = "coordinatorclientext";
    private static final String SERVICEINFO = "serviceinfo";
    private static volatile SysSvcImpl sysservice;
    private static volatile CoordinatorClientExt _coordinator;
    private static volatile ServiceImpl _serviceinfo;
    private final int NUMCLIENTS = 2;
    private final int NUMRUNS = 1;
    private static volatile String nodeid1;
    private static volatile String nodeid2;
    private static volatile String targetVersion1;
    private static volatile String targetVersion2;
    private static final String DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK = "controlNodeUpgradeLock";

    @BeforeClass
    public static void setup() throws Exception {
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(
                "/sys-conf.xml");
        sysservice = (SysSvcImpl) ctx.getBean(SERVICE_BEAN);
        sysservice.start();
        _coordinator = (CoordinatorClientExt) ctx.getBean(COORDINATOR_BEAN);
        _serviceinfo = (ServiceImpl) ctx.getBean(SERVICEINFO);

        nodeid1 = "node1";
        nodeid2 = "node2";
        targetVersion1 = "storageos-1.0.0.0.r500";
        targetVersion2 = "storageos-1.0.0.0.6666";
    }

    @Test
    public void testCoordinatorPersistLock() throws Exception {
        _logger.info("Testing coordinator persistent lock");
        boolean flag = false;
        String leader = null;

        // Clear the lock if anybody hold the lock currently
        leader = _coordinator.getUpgradeLockOwner(DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
        if (leader != null) {
            _coordinator.releasePersistentLock(leader, DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
        }

        /**
         * Single Node Persistent Lock test
         */
        // Ensure node do not have lock at the start
        flag = _coordinator.hasPersistentLock(nodeid1, DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
        Assert.assertFalse(flag);
        leader = _coordinator.getUpgradeLockOwner(DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
        Assert.assertNull(leader);
        // Ensure, the node can get the persistent lock
        flag = _coordinator.getPersistentLock(nodeid1, DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
        Assert.assertTrue(flag);
        // Ensure, the call is not reentrant
        flag = _coordinator.getPersistentLock(nodeid1, DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
        Assert.assertFalse(flag);
        // Ensure, we can retrieve the leader
        flag = _coordinator.hasPersistentLock(nodeid1, DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
        Assert.assertTrue(flag);
        leader = _coordinator.getUpgradeLockOwner(DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
        Assert.assertNotNull(leader);
        Assert.assertEquals(leader, nodeid1);
        // Release the upgrade lock and ensure its been released
        _coordinator.releasePersistentLock(nodeid1, DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
        flag = _coordinator.hasPersistentLock(nodeid1, DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
        Assert.assertFalse(flag);

        /**
         * Two Node Persistent Lock test. Test, if lock can be grabbed by
         * different node, once its released by first node
         */
        flag = _coordinator.getPersistentLock(nodeid1, DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
        Assert.assertTrue(flag);
        flag = _coordinator.getPersistentLock(nodeid2, DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
        Assert.assertFalse(flag);
        leader = _coordinator.getUpgradeLockOwner(DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
        Assert.assertNotNull(leader);
        Assert.assertEquals(leader, nodeid1);
        _coordinator.releasePersistentLock(nodeid1, DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
        flag = _coordinator.getPersistentLock(nodeid2, DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
        Assert.assertTrue(flag);
        leader = _coordinator.getUpgradeLockOwner(DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
        Assert.assertNotNull(leader);
        Assert.assertEquals(leader, nodeid2);
        _coordinator.releasePersistentLock(nodeid2, DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);

        /**
         * Error Tests
         */
        try {
            _coordinator.releasePersistentLock(nodeid1, DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
        } catch (SyssvcInternalException e) {
            Assert.assertNotNull(e);
        }
    }

    @Test
    public void testCoordinatorNonPersistLock() throws Exception {
        _logger.info("Testing coordinator non-persistent lock");
        boolean flag = false;
        String leader = null;
        leader = _coordinator.getRemoteDownloadLeader();
        Assert.assertNull(leader);
        flag = _coordinator.getRemoteDownloadLock(nodeid1);
        Assert.assertTrue(flag);
        flag = _coordinator.hasRemoteDownloadLock(nodeid1);
        Assert.assertTrue(flag);
        flag = _coordinator.hasRemoteDownloadLock(nodeid2);
        Assert.assertFalse(flag);
        leader = _coordinator.getRemoteDownloadLeader();
        Assert.assertNotNull(leader);
        Assert.assertEquals(leader, nodeid1);
        _coordinator.releaseRemoteDownloadLock(nodeid1);
        leader = _coordinator.getRemoteDownloadLeader();
        Assert.assertNull(leader);
        flag = _coordinator.getRemoteDownloadLock(nodeid2);
        Assert.assertTrue(flag);
        flag = _coordinator.hasRemoteDownloadLock(nodeid2);
        Assert.assertTrue(flag);
        _coordinator.releaseRemoteDownloadLock(nodeid2);

        /**
         * Error Tests
         */
        _coordinator.releaseRemoteDownloadLock(nodeid1);
    }

    /*
     * @Test
     * public void testSyssvcBeacon() throws Exception {
     * _logger.info("Testing system service beacon persistent service");
     * RepositoryInfo target = null;
     * target = _coordinator.getTargetRepositoryInfo();
     * Assert.assertNull(target);
     * Assert.assertTrue(_coordinator.setTargetRepositoryInfo(new RepositoryInfo(targetVersion1)));
     * target = _coordinator.getTargetRepositoryInfo();
     * Assert.assertNotNull(target);
     * Assert.assertEquals(targetVersion1, target.toString());
     * Assert.assertTrue(_coordinator.setTargetRepositoryInfo(new RepositoryInfo(targetVersion2)));
     * target = _coordinator.getTargetRepositoryInfo();
     * Assert.assertNotNull(target);
     * Assert.assertEquals(targetVersion2, target.toString());
     * Assert.assertTrue(_coordinator.setTargetRepositoryInfo(null));
     * target = _coordinator.getTargetRepositoryInfo();
     * Assert.assertNotNull(target);
     * }
     */
    @Test
    public void multipleClientTestForUpgradeLock() throws Exception {
        _logger.info("*** multipleClientTestForUpgradeLock start");
        ExecutorService clients = Executors.newFixedThreadPool(NUMCLIENTS);

        for (int i = 0; i < NUMCLIENTS; i++) {
            clients.submit(new Runnable() {
                @Override
                public void run() {
                    String nodeName = Thread.currentThread().getName() + ":9998";
                    _logger.info("Node {} Initialised lock", nodeName);
                    for (int i = 0; i < NUMRUNS; i++) {
                        try {
                            _logger.info(": {} ------ Node: starts loop ------", nodeName);
                            _logger.info(": {} client trying to acquire lock", nodeName);
                            Thread.sleep(50);
                            _coordinator.getPersistentLock(nodeName, DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
                            String currOwnerName = _coordinator.getUpgradeLockOwner(DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
                            _logger.info(": {} is current owner",
                                    currOwnerName);
                            Thread.sleep(50);
                            if (_coordinator.hasPersistentLock(nodeName, DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK)) {
                                Thread.sleep(50);
                                _logger.info(": {} work done. releasing lock", currOwnerName);
                                _coordinator.releasePersistentLock(nodeName, DISTRIBUTED_CONTROL_NODE_UPGRADE_LOCK);
                                _logger.info(": lock release by {}", nodeName);
                                Thread.sleep(100);
                            } else {
                                _logger.info(": {} request failed. retrying.", nodeName);
                                Thread.sleep(50);
                            }
                        } catch (InterruptedException e) {
                            // Ignore this.
                        } catch (Exception e) {
                            _logger.info(": {} transient error ...", nodeName, e);
                        }
                    }
                }
            });
        }
        clients.awaitTermination(5, TimeUnit.SECONDS);
        _logger.info("*** multipleClientTestForUpgradeLock end");
    }

    @Test
    public void multipleClientTestForLeaderLock() throws Exception {
        _logger.info("*** multipleClientTestForLeaderLock start");
        ExecutorService clients = Executors.newFixedThreadPool(NUMCLIENTS);

        for (int i = 0; i < NUMCLIENTS; i++) {
            clients.submit(new Runnable() {
                @Override
                public void run() {
                    String nodeName = Thread.currentThread().getName() + ":9998";
                    _logger.info("Node {} Initialised lock", nodeName);
                    for (int i = 0; i < NUMRUNS; i++) {
                        try {
                            _logger.info(": {} ------ Node: starts loop ------", nodeName);
                            _logger.info(": {} client trying to acquire lock", nodeName);
                            Thread.sleep(50);
                            _coordinator.getRemoteDownloadLock(nodeName);
                            String currOwnerName = _coordinator.getRemoteDownloadLeader();
                            _logger.info(": {} is current owner", currOwnerName);
                            Thread.sleep(50);
                            if (_coordinator.hasRemoteDownloadLock(nodeName)) {
                                Thread.sleep(50);
                                _logger.info(": {} work done. releasing lock", currOwnerName);
                                _coordinator.releaseRemoteDownloadLock(nodeName);
                                _logger.info(": lock release by {}", nodeName);
                                Thread.sleep(100);
                            } else {
                                _logger.info(": {} request failed. retrying.", nodeName);
                                Thread.sleep(50);
                            }
                        } catch (InterruptedException e) {
                            // Ignore this.
                        } catch (Exception e) {
                            _logger.info(": {} transient error ...", nodeName, e);
                        }
                    }
                }
            });
        }
        clients.awaitTermination(5, TimeUnit.SECONDS);
        _logger.info("*** multipleClientTestForLeaderLock end");
    }

    @Test
    public void testNodeIdentifier() {
        List<String> nodelist = _coordinator.getAllNodes();
        System.out.println("Number of Nodes found " + nodelist.size());
        Iterator<String> nodeIter = nodelist.iterator();
        while (nodeIter.hasNext()) {
            String currentnode = nodeIter.next();
            System.out.println("Node ID " + currentnode);
            try {
                RepositoryInfo info = _coordinator.getRepositoryInfo(currentnode);
            } catch (CoordinatorClientException e) {
                System.out.println("Version List is null");
            }
        }
    }

    @Test
    public void testSerialize() throws Exception {
        _logger.info("Testing coordinator serialization");

        _coordinator.getRemoteDownloadLock(nodeid1);

        try {
            HashMap<String, String> map = new HashMap<String, String>();
            map.put("property1", "value1");
            map.put("property2", "value2");

            {
                // test set/get target
                _coordinator.setTargetInfo(new PropertyInfoExt(map));

                PropertyInfoExt props = _coordinator.getTargetInfo(PropertyInfoExt.class);
                Assert.assertTrue("value1".equals(props.getProperty("property1")));
                Assert.assertTrue("value2".equals(props.getProperty("property2")));
            }

            {
                // test publish/get node info
                _coordinator.setNodeSessionScopeInfo(new PropertyInfoExt(map));
                PropertyInfoExt props = _coordinator.getNodeInfo("standalone", PropertyInfoExt.class);
                Assert.assertTrue("value1".equals(props.getProperty("property1")));
                Assert.assertTrue("value2".equals(props.getProperty("property2")));
            }
        } catch (Exception e) {
            Assert.assertTrue(false);
        }

        _coordinator.releaseRemoteDownloadLock(nodeid1);
    }

    @AfterClass
    public static void stop() {
        try {
            sysservice.stop();
        } catch (Exception e) {
            _logger.error("Error Stopping the system service");
        }
    }
}
