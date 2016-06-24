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
import com.emc.storageos.services.util.Waiter;
import java.text.DecimalFormat;
import java.text.MessageFormat;
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
public class DrSiteNetworkMonitor extends DrHealthMonitor {

    private static final Logger _log = LoggerFactory.getLogger(DrSiteNetworkMonitor.class);
    private AlertsLogger _alertLog = AlertsLogger.getAlertsLogger();

    @Autowired
    private MailHandler mailHandler;

    @Autowired
    private DrUtil drUtil;

    @Autowired
    private CoordinatorClient coordinatorClient;

    private static final int SOCKET_TEST_PORT = 443;
    private static final int NETWORK_SLOW_THRESHOLD = 150;
    private static final int NETWORK_TIMEOUT = 10 * 1000;

    public DrSiteNetworkMonitor() {
    }

    @Override
    public void tick() {
        if (shouldStartOnCurrentSite() && drUtil.isLeaderNode()) {
            checkPing();
        }
    }

    /**
     * Whether we should bring up network monitor. Only active site(or degraded), or paused standby site need run network monitor 
     * 
     * @return true if we should start it
     */
    private boolean shouldStartOnCurrentSite() {
        if (drUtil.isActiveSite()) {
            return true;
        }
        
        Site localSite = drUtil.getLocalSite();
        SiteState state = localSite.getState();
        if (state == SiteState.STANDBY_PAUSED || state == SiteState.ACTIVE_DEGRADED) {
            return true;
        }
        _log.debug("This site is not active site or standby paused, no need to do network monitor");
        return false;
    }

    private void checkPing() {
        Site localSite = drUtil.getLocalSite();
        
        SiteNetworkState localNetworkState = drUtil.getSiteNetworkState(localSite.getUuid());
        if (!NetworkHealth.GOOD.equals(localNetworkState.getNetworkHealth()) || localNetworkState.getNetworkLatencyInMs() != 0) {
            localNetworkState.setNetworkLatencyInMs(0);
            localNetworkState.setNetworkHealth(NetworkHealth.GOOD);
            coordinatorClient.setTargetInfo(localSite.getUuid(), localNetworkState);
        }

        for (Site site : drUtil.listSites()){
            if (drUtil.isLocalSite(site)) {
                continue; // skip local site
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
                AlertsLogger.getAlertsLogger().warn(String.format("Network for standby %s is Broken:" +
                        "Latency was reported as %f ms",site.getName(),ping));
            }
            else if (ping < 0) {
                siteNetworkState.setNetworkHealth(NetworkHealth.BROKEN);
                _log.error("Network for standby {} is broken",site.getName());
                AlertsLogger.getAlertsLogger().error(String.format("Network for standby %s is Broken:" +
                        "Latency was reported as %s ms",site.getName(),ping));
            }
            else {
                siteNetworkState.setNetworkHealth(NetworkHealth.GOOD);
            }

            coordinatorClient.setTargetInfo(site.getUuid(), siteNetworkState);

            if (drUtil.isActiveSite()) {
                SiteState state = site.getState();
                if (SiteState.STANDBY_ADDING == state || SiteState.STANDBY_RESUMING == state) {
                    _log.info("Skip mail alert during add-standby or resume-standby for {}", site.getUuid());
                    continue;
                }
                
                if (!NetworkHealth.BROKEN.equals(previousState)
                        && NetworkHealth.BROKEN.equals(siteNetworkState.getNetworkHealth())){
                    //Add to systemevent log
                    _alertLog.error(MessageFormat.format("Network connection to site %s has been broken.",site.getName()));
                    //send email alert
                    mailHandler.sendSiteNetworkBrokenMail(site);
                }
            }
        }
    }

};
