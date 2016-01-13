/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.util;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;

public class DrSiteNetworkMonitor implements Runnable{

    private static final Logger _log = LoggerFactory.getLogger(DrSiteNetworkMonitor.class);

    private CoordinatorClient coordinatorClient;
    private DrUtil drUtil;
    private String myNodeId;
    private MailHandler mailHandler;

    private String ZOOKEEPER_MODE_LEADER = "leader";
    private String NETWORK_HEALTH_BROKEN = "Broken";
    private String NETWORK_HEALTH_GOOD = "Good";
    private String NETWORK_HEALTH_SLOW = "Slow";

    public DrSiteNetworkMonitor(String myNodeId, CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
        this.drUtil = new DrUtil(coordinatorClient);
        this.myNodeId = myNodeId;
        mailHandler = new MailHandler();
    }

    public void run() {

        try {
            checkPing();
        } catch (Exception e) {
            //try catch exception to make sure next scheduled run can be launched.
            _log.error("Error occurs when monitor standby network", e);
        }

    }

    private void checkPing() {

        //Only leader on active site will test ping (no networking info if active down?)
        String zkState = drUtil.getLocalCoordinatorMode(myNodeId);


        if (ZOOKEEPER_MODE_LEADER.equals(zkState)) {

            int testPort = 4443;

            //I'm the leader
            for (Site site : drUtil.listStandbySites()){
                String previousState = site.getNetworkHealth();
                String host = site.getVip();
                double ping = testPing(host,4443);
                _log.info("Ping: "+ping);
                site.setPing(ping);
                if (ping > 150) {
                    site.setNetworkHealth(NETWORK_HEALTH_SLOW);
                    _log.warn("Network for standby {} is slow",site.getName());
                }
                else if (ping < 0) {
                    site.setNetworkHealth(NETWORK_HEALTH_BROKEN);
                    _log.error("Network for standby {} is broken",site.getName());
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
    }

    /**
     * Connect using layer4 (sockets)
     *
     * @return delay if the specified host responded, -1 if failed
     */
    private double testPing(String hostAddress, int port) {
        InetAddress inetAddress = null;
        InetSocketAddress socketAddress = null;
        SocketChannel sc = null;
        long timeToRespond = -1;
        long start, stop;

        try {
            inetAddress = InetAddress.getByName(hostAddress);
        } catch (UnknownHostException e) {
            _log.error("Problem, unknown host:",e);
        }

        try {
            socketAddress = new InetSocketAddress(inetAddress, port);
        } catch (IllegalArgumentException e) {
            _log.error("Problem, port may be invalid:",e);
        }

        // Open the channel, set it to non-blocking, initiate connect
        try {
            sc = SocketChannel.open();
            sc.configureBlocking(true);
            start = System.nanoTime();
            if (sc.connect(socketAddress)) {
                stop = System.nanoTime();
                timeToRespond = (stop - start);
            }
        } catch (IOException e) {
            _log.error("Problem, connection could not be made:",e);
        }

        try {
            sc.close();
        } catch (IOException e) {
            _log.error("Error closing socket during latency test",e);
        }

        //The ping failed, return -1
        if (timeToRespond == -1) {
            return -1;
        }

        //the ping suceeded, convert from ns to ms with 3 decimals
        timeToRespond = timeToRespond/1000;
        return timeToRespond/1000.0;
    }

};