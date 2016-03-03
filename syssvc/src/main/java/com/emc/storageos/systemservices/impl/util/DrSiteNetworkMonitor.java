/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.util;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteNetworkState;
import com.emc.storageos.coordinator.client.model.SiteNetworkState.NetworkHealth;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.services.util.AlertsLogger;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import java.text.DecimalFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;


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

        Site active = drUtil.getActiveSite();
        SiteNetworkState activeNetworkState = drUtil.getSiteNetworkState(active.getUuid());
        if (!NetworkHealth.GOOD.equals(activeNetworkState.getNetworkHealth()) || activeNetworkState.getNetworkLatencyInMs() != 0) {

            activeNetworkState.setNetworkLatencyInMs(0);
            activeNetworkState.setNetworkHealth(NetworkHealth.GOOD);
            coordinatorClient.setTargetInfo(active.getUuid(), activeNetworkState);
        }

        for (Site site : drUtil.listStandbySites()){
            if (SiteState.STANDBY_ADDING.equals(site.getState()) || SiteState.STANDBY_REMOVING.equals(site.getState())){
                _log.info("Skip site {} for network health check", site.getSiteShortId());
                continue;
            }
            SiteNetworkState siteNetworkState = drUtil.getSiteNetworkState(site.getUuid());
            NetworkHealth previousState = siteNetworkState.getNetworkHealth();
            String host = site.getVipEndPoint();
            double ping = drUtil.testPing(host, SOCKET_TEST_PORT, NETWORK_TIMEOUT);

            //if ping successful get an average, format to 3 decimal places
            if( ping != -1){
                ping = (ping + drUtil.testPing(host, SOCKET_TEST_PORT, NETWORK_TIMEOUT) + drUtil.testPing(host, SOCKET_TEST_PORT,
                        NETWORK_TIMEOUT)) / 3;
                DecimalFormat df = new DecimalFormat("#.###");
                ping = Double.parseDouble(df.format(ping));
            }

            _log.info("Ping: "+ping);
            siteNetworkState.setNetworkLatencyInMs(ping);

            if (ping > NETWORK_SLOW_THRESHOLD) {
                siteNetworkState.setNetworkHealth(NetworkHealth.SLOW);
                _log.warn("Network for standby {} is slow",site.getName());
                AlertsLogger.getAlertsLogger().warn(String.format("Network for standby {} is Broken:" +
                        "Latency was reported as {} ms",site.getName(),ping));
            }
            else if (ping < 0) {
                siteNetworkState.setNetworkHealth(NetworkHealth.BROKEN);
                _log.error("Network for standby {} is broken",site.getName());
                AlertsLogger.getAlertsLogger().error(String.format("Network for standby {} is Broken:" +
                        "Latency was reported as {} ms",site.getName(),ping));
            }
            else {
                siteNetworkState.setNetworkHealth(NetworkHealth.GOOD);
            }

            coordinatorClient.setTargetInfo(site.getUuid(), siteNetworkState);

            if (!NetworkHealth.BROKEN.equals(previousState)
                    && NetworkHealth.BROKEN.equals(siteNetworkState.getNetworkHealth())){
                //send email alert
                mailHandler.sendSiteNetworkBrokenMail(site);
            }
        }
    }

};
