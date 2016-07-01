/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedDoubleBarrier;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.storageos.systemservices.impl.upgrade.LocalRepository;

/**
 * A DR monitor working in Standby site to monitor zookeeper status in DR environment. Zookeeper in Standby site
 * works in 'observer' mode (see https://zookeeper.apache.org/doc/r3.3.3/zookeeperObservers.html). Normally it becomes
 * read-only after losing connection to Active site. But we still would make it work so that other services 
 * like dbsvc, UI etc could be up and running on Standby site. So we invent this monitor to address the following
 * situations - 
 * 
 * 1) If Standby site loses connection to Active site, the leader monitor(the node who is holding VIP) reconfigures 
 * zookeeper cluster to 'paritipant' mode(read-writable).
 * 2) If Active site comes back, all nodes reconfigures themselves and reconnect back to Active site as 'observer' 
 * 
 */
public class DrZkHealthMonitor extends DrHealthMonitor {
    private static final Logger log = LoggerFactory.getLogger(DrZkHealthMonitor.class);
    
    private static final String DR_SWITCH_TO_ZK_OBSERVER_BARRIER = "/config/disasterRecoverySwitchToZkObserver";
    private static final int DR_SWITCH_BARRIER_TIMEOUT = 180; // barrier timeout in seconds

    private CoordinatorClientExt coordinatorExt;
    private DistributedDoubleBarrier switchToZkObserverBarrier;
    private String initZkMode; // ZK mode during syssvc startup
    private DrUtil drUtil;
    
    public DrZkHealthMonitor() {
    }

    @Override
    public void start() {
        CoordinatorClient coordinator = getCoordinator().getCoordinatorClient();
        String barrierPath = String.format("%s/%s%s", ZkPath.SITES, coordinator.getSiteId(), DR_SWITCH_TO_ZK_OBSERVER_BARRIER);
        switchToZkObserverBarrier = coordinator.getDistributedDoubleBarrier(barrierPath, coordinatorExt.getNodeCount());
        super.start();
    }
    
    @Override
    public void tick() {
        try {
            String myNodeId = coordinatorExt.getMyNodeId();
            String localZkMode = drUtil.getCoordinatorMode(myNodeId);
            if (initZkMode == null) {
                initZkMode = localZkMode;
            }

            log.info("Local zookeeper mode: {} ",localZkMode);

            if(coordinatorExt.isVirtualIPHolder()){
                log.info("Local node has vip, monitor other node zk states");
                checkAndReconfigSiteZKModes();
            }

            /*
             *  If local ZK (in the standby site) is running on its own independently (leader, follower or standby mode)
             *  or it could not startup at all (state == null),
             *  We will try to switch local ZK to observe mode if the active site is running well.
            */
            if (localZkMode == null || drUtil.isParticipantNode(localZkMode)) {
                if (localZkMode != null && drUtil.isLeaderNode(localZkMode)) {
                    // node is in participant mode, update the local site state accordingly
                    checkAndUpdateLocalSiteState();
                }

                // check if active site is back
                if (coordinatorExt.isActiveSiteHealthy()) {
                    log.info("Active site is back. Reconfig coordinatorsvc to observer mode");
                    reconnectZKToActiveSite();
                } else {
                    log.info("Active site is unavailable. Keep coordinatorsvc in current state {}", localZkMode);
                }
            }
        }catch(Exception e){
            log.error("Exception while monitoring node state: ", e);
        }
    }

    /**
     * Update the standby site state when the active site is lost.
     * if SYNCED, change it to PAUSED.
     * if SYNCING/RESUMING/ADDING, change it to ERROR since it will never finish without the active site.
     */
    private void checkAndUpdateLocalSiteState() {
        Site localSite = drUtil.getLocalSite();

        SiteState state = localSite.getState();
        if (SiteState.STANDBY_SYNCED.equals(state) || SiteState.STANDBY_INCR_SYNCING.equals(state)) {
            log.info("Updating local site from {} to STANDBY_PAUSED since active is unreachable",
                    state);
            localSite.setState(SiteState.STANDBY_PAUSED);
            coordinatorExt.getCoordinatorClient().persistServiceConfiguration(localSite.toConfiguration());
            coordinatorExt.rescheduleDrSiteNetworkMonitor();
        } else if (SiteState.STANDBY_SYNCING.equals(state) ||
                SiteState.STANDBY_RESUMING.equals(state) ||
                SiteState.STANDBY_ADDING.equals(state)){
            log.info("Updating local site from {} to STANDBY_ERROR since active is unreachable",
                    localSite.getState());

            localSite.setLastState(state);
            localSite.setState(SiteState.STANDBY_ERROR);
            coordinatorExt.getCoordinatorClient().persistServiceConfiguration(localSite.toConfiguration());
        }
    }

    /**
     * make sure that all local site nodes are in correct zk mode
     */
    private void checkAndReconfigSiteZKModes() {
        List<String> readOnlyNodes = new ArrayList<>();
        List<String> observerNodes = new ArrayList<>();
        int numOnline = 0;

        for(String node : coordinatorExt.getAllNodeIds()){

            String nodeState=drUtil.getCoordinatorMode(node);
            if (nodeState==null){
                log.debug("State for {}: null",node);
                continue;
            }

            else if(DrUtil.ZOOKEEPER_MODE_READONLY.equals(nodeState)){
                // Found another node in read only
                readOnlyNodes.add(node);
            }
            else if (DrUtil.ZOOKEEPER_MODE_OBSERVER.equals(nodeState)) {
                // Found another node in observer
                observerNodes.add(node);
            }
            log.debug("State for {}: {}",node,nodeState);
            numOnline++;
        }

        int numParticipants = numOnline - readOnlyNodes.size() - observerNodes.size();
        int quorum = coordinatorExt.getNodeCount() / 2 + 1;

        log.debug("Observer nodes: {}",observerNodes.size());
        log.debug("Read Only nodes: {}",readOnlyNodes.size());
        log.debug("Participant nodes: {}",numParticipants);
        log.debug("nodes Online: {}",numOnline);

        // if there is a participant we need to reconfigure or it will be stuck there
        // if there are only participants no need to reconfigure
        // if there are only read only nodes and we have quorum we need to reconfigure
        if(0 < numParticipants && numParticipants < numOnline) {
            log.info("Nodes must have consistent zk mode. Reconfiguring all nodes to participant: {}",
                    observerNodes.addAll(readOnlyNodes));
            reconfigZKToWritable(observerNodes, readOnlyNodes);
        }
        else if (readOnlyNodes.size() == numOnline && numOnline >= quorum){
            log.info("A quorum of nodes are read-only, Reconfiguring nodes to participant: {}",readOnlyNodes);
            reconfigZKToWritable(observerNodes, readOnlyNodes);
        }
    }

    /**
     * Reconnect to zookeeper in active site. 
     */
    private void reconnectZKToActiveSite() {
        LocalRepository localRepository = LocalRepository.getInstance();
        try {
            boolean allEntered = switchToZkObserverBarrier.enter(DR_SWITCH_BARRIER_TIMEOUT, TimeUnit.SECONDS);
            if (allEntered) {
                try {
                    localRepository.reconfigCoordinator("observer");
                } finally {
                    leaveZKDoubleBarrier(switchToZkObserverBarrier, DR_SWITCH_TO_ZK_OBSERVER_BARRIER);
                }
                localRepository.restartCoordinator("observer");
            } else {
                log.warn("All nodes unable to enter barrier {}. Try again later", DR_SWITCH_TO_ZK_OBSERVER_BARRIER);
            }
        } catch (Exception ex) {
            log.warn("Unexpected errors during switching back to zk observer. Try again later. {}", ex);
        } 
    }
    
    /**
     * reconfigure ZooKeeper to participant mode within the local site
     *
     * @param barrier barrier to leave
     * @param path for logging barrier
     * @return true for successful, false for success unknown
     */
    private void leaveZKDoubleBarrier(DistributedDoubleBarrier barrier, String path){
        try {
            log.info("Leaving the barrier {}",path);
            boolean leaved = barrier.leave(DR_SWITCH_BARRIER_TIMEOUT, TimeUnit.SECONDS);
            if (!leaved) {
                log.warn("Unable to leave barrier for {}", path);
            }
        } catch (Exception ex) {
            log.warn("Unexpected errors during leaving barrier",ex);
        }
    }

    /**
     * reconfigure ZooKeeper to participant mode within the local site
     *
     * @param observerNodes to be reconfigured
     * @param readOnlyNodes to be reconfigured
     */
    public void reconfigZKToWritable(List<String> observerNodes,List<String> readOnlyNodes) {
        log.info("Standby is running in read-only mode due to connection loss with active site. " +
                "Reconfig coordinatorsvc of all nodes to writable");

        try{
            boolean reconfigLocal = false;

            // if zk is switched from observer mode to participant, reload syssvc
            for(String node:observerNodes){
                //The local node cannot reboot itself before others
                if(node.equals(coordinatorExt.getMyNodeId())){
                    reconfigLocal=true;
                    continue;
                }
                LocalRepository localRepository=LocalRepository.getInstance();
                localRepository.remoteReconfigCoordinator(node, "participant");
                localRepository.remoteRestartCoordinator(node, "participant");
            }

            for(String node:readOnlyNodes){
                //The local node cannot reboot itself before others
                if(node.equals(coordinatorExt.getMyNodeId())){
                    reconfigLocal=true;
                    continue;
                }
                LocalRepository localRepository=LocalRepository.getInstance();
                localRepository.remoteReconfigCoordinator(node, "participant");
                localRepository.remoteRestartCoordinator(node, "participant");
            }

            //reconfigure local node last
            if (reconfigLocal){
                coordinatorExt.reconfigZKToWritable();
            }

        }catch(Exception ex){
            log.warn("Unexpected errors during switching back to zk observer. Try again later. {}", ex.toString());
        }
    }

    public CoordinatorClientExt getCoordinator() {
        return coordinatorExt;
    }

    public void setCoordinator(CoordinatorClientExt coordinatorClientExt) {
        this.coordinatorExt = coordinatorClientExt;
    }

    public DrUtil getDrUtil() {
        return drUtil;
    }

    public void setDrUtil(DrUtil drUtil) {
        this.drUtil = drUtil;
    }
    
}