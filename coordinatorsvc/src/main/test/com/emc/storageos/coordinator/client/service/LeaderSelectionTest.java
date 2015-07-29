/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.coordinator.client.service;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import com.emc.storageos.coordinator.client.service.impl.LeaderSelectorListenerImpl;

/**
 * LeaderSelector test is a set of routines to test validity of the leader selector framework
 */
public class LeaderSelectionTest extends CoordinatorTestBase {
    private static final Logger logger = LoggerFactory.getLogger(LeaderSelectionTest.class);
    public static final String LATCH_PATH = "leader_latch";
    public static final String LATCH_NAME = "leader_processor";
    final static int NUMCLIENTS = 2;
    final static int NUMRUN = 3;
    final static int DELAY = 1;  // 1 sec delay
    final static int INTERVAL = 5; // 5 sec interval between jobs

    private static final ArrayList<String> leaderMonitor = new ArrayList(NUMCLIENTS * NUMRUN);
    private static final Lock monitorLock = new ReentrantLock();
    private static final ArrayList<LeaderSelector> leaders = new ArrayList<LeaderSelector>(NUMCLIENTS);

    /**
     * Simulates multiple clients accessing persistent lock API simultaneously.
     * 
     * @throws Exception
     */
    @Test
    public void leaderSelectionTest() throws Exception {
        logger.info("*** Leader Seleciton Test start");
        ExecutorService clients = Executors.newFixedThreadPool(NUMCLIENTS);

        for (int i = 0; i < NUMCLIENTS; i++) {
            final int count = i;
            clients.submit(new Runnable() {
                @Override
                public void run() {
                    String leaderName = LATCH_NAME + '_' + (count + 1);
                    LeaderSelector leader = null;
                    try {
                        TestProcessor processor = new TestProcessor(leaderName);
                        leader = connectClient().getLeaderSelector(LATCH_PATH, processor);
                    } catch (Exception e) {
                        logger.info(": {} leaderSelectionTest could not get coordinator client", e);
                        Assert.assertNull(e);
                        return;
                    }
                    logger.info(": ### Initialized LeaderSelector {} ###", leaderName);

                    leader.start();
                    leader.requeue();
                    synchronized (leaders) {
                        leaders.add(leader);
                    }
                }
            });
        }
        synchronized (leaderMonitor) {
            while (leaderMonitor.size() < NUMCLIENTS * NUMRUN) {
                leaderMonitor.wait();
            }
        }

        synchronized (leaders) {
            for (int i = 0; i < NUMCLIENTS; i++) {
                leaders.get(i).close();
            }
        }

        synchronized (leaderMonitor) {
            for (int i = 0; i < NUMCLIENTS; i++) {
                for (int j = 0; j < NUMRUN; j++) {
                    logger.info("Leadership : " + leaderMonitor.get(i * NUMRUN + j));
                }
                String first = leaderMonitor.get(0).substring(0, 17);
                for (int j = 1; j < NUMRUN; j++) {
                    Assert.assertTrue(leaderMonitor.get(j).startsWith(first));
                }
            }
        }

        logger.info("*** LeaderSelector end");
    }

    public static class TestProcessor extends LeaderSelectorListenerImpl {

        public String name;

        public TestProcessor(String name) {
            this.name = name;
        }

        protected void startLeadership() throws Exception {

            Thread.sleep(DELAY * 1000);
            for (int count = 0; count < NUMRUN; count++) {
                String message = name + '-' + (count + 1);
                synchronized (leaderMonitor) {
                    leaderMonitor.add(message);
                    leaderMonitor.notifyAll();
                }
                logger.info("Adding message : " + message);
                Thread.sleep(INTERVAL * 1000);
            }

        }

        protected void stopLeadership() {
        }
    }

}
