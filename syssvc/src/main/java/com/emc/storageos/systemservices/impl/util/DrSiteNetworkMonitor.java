/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.util;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.services.util.AlertsLogger;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.DecimalFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * A thread started in syssvc to monitor network health between active and standby site.
 * Network health is determined by checking socket connection latency to SOCKET_TEST_PORT
 * If latency is less than 150ms then Network health is "Good", if it is greater then Network Health is "Slow"
 * If the testPing times out or fails to connect then pin is -1 and NetworkHealth is "Broken""
 */
public class DrSiteNetworkMonitor implements Runnable{

    private static final Logger _log = LoggerFactory.getLogger(DrSiteNetworkMonitor.class);

    @Autowired
    private CoordinatorClientExt coordinator;

    @Autowired
    private MailHandler mailHandler;

    @Autowired
    private DrUtil drUtil;

    private CoordinatorClient coordinatorClient;
    private String myNodeId;


    private static final int NETWORK_MONITORING_INTERVAL = 60; // in seconds
    private static final String NETWORK_HEALTH_BROKEN = "Broken";
    private static final String NETWORK_HEALTH_GOOD = "Good";
    private static final String NETWORK_HEALTH_SLOW = "Slow";
    public static final String ZOOKEEPER_MODE_LEADER = "leader";
    public static final String ZOOKEEPER_MODE_STANDALONE = "standalone";

    private static final int SOCKET_TEST_PORT = 443;
    private static final int NETWORK_SLOW_THRESHOLD = 150;
    private static final int NETWORK_TIMEOUT = 10 * 1000;

    public DrSiteNetworkMonitor() {
    }

    public void init() {
        coordinatorClient = coordinator.getCoordinatorClient();
        myNodeId = coordinator.getMyNodeId();
    }

    public void run() {
        _log.info("Start monitoring local networkMonitor status on active site");
        ScheduledExecutorService exe = Executors.newScheduledThreadPool(1);
        exe.scheduleAtFixedRate(networkMonitor, 0, NETWORK_MONITORING_INTERVAL, TimeUnit.SECONDS);
    }

    private Runnable networkMonitor = new Runnable(){
        public void run() {

            //Only leader on active site will test ping (no networking info if active down?)
            String zkState = drUtil.getLocalCoordinatorMode(myNodeId);

            //Check if this node is the leader
            if (!ZOOKEEPER_MODE_LEADER.equals(zkState) && !ZOOKEEPER_MODE_STANDALONE.equals(zkState)) {
                return;
            }

            try {
                checkPing();
            } catch (Exception e) {
                //try catch exception to make sure next scheduled run can be launched.
                _log.error("Error occurs when monitor standby network", e);
            }

        }
    };

    private void checkPing() {
        
        if (!drUtil.isActiveSite()) {
            _log.info("This site is not active site, no need to do network monitor");
            return;
        }

        //Check that active site is set to good Network Health
        Site active = drUtil.getActiveSite();
        if (!NETWORK_HEALTH_GOOD.equals(active.getNetworkHealth()) || active.getNetworkLatencyInMs() != 0) {
            active.setNetworkHealth(NETWORK_HEALTH_GOOD);
            active.setNetworkLatencyInMs(0);
            coordinatorClient.persistServiceConfiguration(active.toConfiguration());
        }

        for (Site site : drUtil.listStandbySites()){
            String previousState = site.getNetworkHealth();
            String host = site.getVip();
            double ping = testPing(host,SOCKET_TEST_PORT);

            //if ping successful get an average, format to 3 decimal places
            if( ping != -1){
                ping = (ping + testPing(host,SOCKET_TEST_PORT) + testPing(host,SOCKET_TEST_PORT))/3;
                DecimalFormat df = new DecimalFormat("#.###");
                ping = Double.parseDouble(df.format(ping));
            }

            _log.info("Ping: "+ping);
            site.setNetworkLatencyInMs(ping);
            if (ping > NETWORK_SLOW_THRESHOLD) {
                site.setNetworkHealth(NETWORK_HEALTH_SLOW);
                _log.warn("Network for standby {} is slow",site.getName());
                AlertsLogger.getAlertsLogger().warn(String.format("Network for standby {} is Broken:" +
                        "Latency was reported as {} ms",site.getName(),ping));
            }
            else if (ping < 0) {
                site.setNetworkHealth(NETWORK_HEALTH_BROKEN);
                _log.error("Network for standby {} is broken",site.getName());
                AlertsLogger.getAlertsLogger().error(String.format("Network for standby {} is Broken:" +
                        "Latency was reported as {} ms",site.getName(),ping));
            }
            else {
                site.setNetworkHealth(NETWORK_HEALTH_GOOD);
            }

            coordinatorClient.persistServiceConfiguration(site.toConfiguration());

            if (!NETWORK_HEALTH_BROKEN.equals(previousState)
                    && NETWORK_HEALTH_BROKEN.equals(site.getNetworkHealth())){
                //send email alert
                mailHandler.sendSiteNetworkBrokenMail(site);
            }
        }
    }

    /**
     * Connect using sockets
     *
     * @return delay in ms if the specified host responded, -1 if failed
     */
    private double testPing(String hostAddress, int port) {
        InetAddress inetAddress = null;
        InetSocketAddress socketAddress = null;
        Socket socket = new Socket();
        long timeToRespond = -1;
        long start, stop;

        try {
            inetAddress = InetAddress.getByName(hostAddress);

            socketAddress = new InetSocketAddress(inetAddress, port);

            start = System.nanoTime();
            socket.connect(socketAddress,NETWORK_TIMEOUT);
            stop = System.nanoTime();
            timeToRespond = (stop - start);
        } catch (Exception e) {
            _log.error(String.format("Fail to check cross-site network latency to node {} with Exception: ",hostAddress),e);
            return -1;
        } finally {
            try {
                if (socket.isConnected()) {
                    socket.close();
                }
            } catch (Exception e) {
                _log.error(String.format("Fail to close connection to node {} with Exception: ",hostAddress),e);
            }
        }

        //the ping suceeded, convert from ns to ms
        return timeToRespond/1000000.0;
    }

};