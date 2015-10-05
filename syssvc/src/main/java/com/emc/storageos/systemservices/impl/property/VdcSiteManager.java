/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.property;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.curator.framework.recipes.barriers.DistributedDoubleBarrier;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.PowerOffState;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.util.VdcConfigUtil;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.exceptions.InvalidLockOwnerException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.util.AbstractManager;

/**
 * Manage configuration properties for multivdc and disaster recovery. It listens on
 * SiteInfo znode changes. Once getting notified, it fetch vdc config from local db
 * or standby sites config from zk and update vdcconfig.properties on local disk. Genconfig 
 * is supposed to be executed later to apply the new config changes.
 * 
 * Data revision change and simulatenous cluster poweroff are also managed here
 */
public class VdcSiteManager extends AbstractManager {
    private static final Logger log = LoggerFactory.getLogger(VdcSiteManager.class);

    private static final String VDC_IDS_KEY = "vdc_ids";

    private DbClient dbClient;

    // local and target info properties
    private PropertyInfoExt localVdcPropInfo;
    private PropertyInfoExt targetVdcPropInfo;
    private PowerOffState targetPowerOffState;
    
    private static final String POWEROFFTOOL_COMMAND = "/etc/powerofftool";

    // set to 2.5 minutes since it takes over 2m for ssh to timeout on non-reachable hosts
    private static final long SHUTDOWN_TIMEOUT_MILLIS = 150000;
    
    // data revision time out - 11 minutes
    private static final long DATA_REVISION_WAIT_TIMEOUT_SECONDS = 300;
    
    private SiteInfo targetSiteInfo;
    
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    protected URI getWakeUpUrl() {
        return SysClientFactory.URI_WAKEUP_VDC_MANAGER;
    }

    /**
     * Register site info listener to monitor site changes
     */
    private void addSiteInfoListener() {
        try {
            coordinator.getCoordinatorClient().addNodeListener(new SiteInfoListener());
        } catch (Exception e) {
            log.error("Fail to add node listener for site info target znode", e);
            throw APIException.internalServerErrors.addListenerFailed();
        }
        log.info("Successfully added node listener for site info target znode");
    }

    /**
     * the listener class to listen the site target node change.
     */
    class SiteInfoListener implements NodeListener {
        public String getPath() {
            return String.format("/sites/%s/config/%s/%s", coordinator.getCoordinatorClient().getSiteId(), SiteInfo.CONFIG_KIND, SiteInfo.CONFIG_ID);
        }

        /**
         * called when user update the site
         */
        @Override
        public void nodeChanged() {
            log.info("Site info changed. Waking up the vdc manager...");
            wakeup();
        }

        /**
         * called when connection state changed.
         */
        @Override
        public void connectionStateChanged(State state) {
            log.info("Site info connection state changed to {}", state);
            if (state.equals(State.CONNECTED)) {
                log.info("Curator (re)connected. Waking up the vdc manager...");
                wakeup();
            }
        }
    }

    
    @Override
    protected void innerRun() {
        final String svcId = coordinator.getMySvcId();

        addSiteInfoListener();

        while (doRun) {
            log.debug("Main loop: Start");

            // Step0: check if we have the vdc lock
            boolean hasLock;
            try {
                hasLock = coordinator.hasPersistentLock(svcId, vdcLockId);
            } catch (Exception e) {
                log.info("Step1: Failed to verify if the current node has the vdc lock ", e);
                retrySleep();
                continue;
            }

            if (hasLock) {
                try {
                    coordinator.releasePersistentLock(svcId, vdcLockId);
                    log.info("Step0: Released vdc lock for node: {}", svcId);
                    wakeupOtherNodes();
                } catch (InvalidLockOwnerException e) {
                    log.error("Step0: Failed to release the vdc lock: Not owner.");
                } catch (Exception e) {
                    log.info("Step0: Failed to release the vdc lock and will retry: {}", e.getMessage());
                    retrySleep();
                    continue;
                }
            }

            // Step1: publish current state, and set target if empty
            try {
                initializeLocalAndTargetInfo();
            } catch (Exception e) {
                log.info("Step1b failed and will be retried: {}", e.getMessage());
                retrySleep();
                continue;
            }
            
            // Step2: power off if all nodes agree.
            log.info("Step2: Power off if poweroff state != NONE. {}", targetPowerOffState);
            try {
                gracefulPoweroffCluster();
            } catch (Exception e) {
                log.error("Step2: Failed to poweroff. {}", e);
            }
            
            log.info("Step3: If VDC configuration is changed update");
            if (vdcPropertiesChanged()) {
                log.info("Step3: Current vdc properties are not same as target vdc properties. Updating.");
                log.debug("Current local vdc properties: " + localVdcPropInfo);
                log.debug("Target vdc properties: " + targetVdcPropInfo);

                try {
                    updateVdcProperties(svcId);
                } catch (Exception e) {
                    log.info("Step2: VDC properties update failed and will be retried: {}", e.getMessage());
                    // Restart the loop immediately so that we release the upgrade lock.
                    continue;
                }
                continue;
            }

            // Step3: change data revision
            String targetDataRevision = targetSiteInfo.getTargetDataRevision();
            log.info("Step3: check if target data revision is changed - {}", targetDataRevision);
            try {
                String localRevision = localRepository.getDataRevision();
                log.info("Step3: local data revision is {}", localRevision);
                if (!targetSiteInfo.isNullTargetDataRevision() && !targetDataRevision.equals(localRevision)) {
                    updateDataRevision();
                    continue;
                }
            } catch (Exception e) {
                log.error("Step3: Failed to update data revision. {}", e);
                continue;
            }
            
            // Step4: sleep
            log.info("Step4: sleep");
            longSleep();
        }
    }

    /**
     * Initialize local and target info
     *
     * @throws Exception
     */
    private void initializeLocalAndTargetInfo() throws Exception {
        // set target if empty
        targetSiteInfo = coordinator.getTargetInfo(SiteInfo.class);
        if (targetSiteInfo == null) {
            targetSiteInfo = new SiteInfo();
        }

        // Initialize vdc prop info
        localVdcPropInfo = localRepository.getVdcPropertyInfo();
        targetVdcPropInfo = loadVdcConfigFromDatabase();
        if (localVdcPropInfo.getProperty(VdcConfigUtil.VDC_CONFIG_VERSION) == null) {
            localVdcPropInfo = new PropertyInfoExt(targetVdcPropInfo.getAllProperties());
            localVdcPropInfo.addProperty(VdcConfigUtil.VDC_CONFIG_VERSION,
                    String.valueOf(targetSiteInfo.getVdcConfigVersion()));
            localRepository.setVdcPropertyInfo(localVdcPropInfo);

            String vdc_ids = targetVdcPropInfo.getProperty(VDC_IDS_KEY);
            String[] vdcIds = vdc_ids.split(",");
            if (vdcIds.length > 1) {
                log.info("More than one Vdc, rebooting");
                reboot();
            }
        }
        
        targetPowerOffState = coordinator.getTargetInfo(PowerOffState.class);
        if (targetPowerOffState == null) {
            // only control node can set target
            try {
                // Set the updated propperty info in coordinator
                coordinator.setTargetInfo(new PowerOffState(PowerOffState.State.NONE));
                targetPowerOffState = coordinator.getTargetInfo(PowerOffState.class);
                log.info("Step1b: Target poweroff state set to: {}", PowerOffState.State.NONE);
            } catch (CoordinatorClientException e) {
                log.info("Step1b: Wait another control node to set target");
                retrySleep();
                throw e;
            }
        }

    }

    /**
     * Load the vdc vonfiguration from the database
     *
     * @return
     */
    private PropertyInfoExt loadVdcConfigFromDatabase() {
        VdcConfigUtil vdcConfigUtil = new VdcConfigUtil();
        vdcConfigUtil.setDbclient(dbClient);
        vdcConfigUtil.setCoordinator(coordinator.getCoordinatorClient());
        return new PropertyInfoExt(vdcConfigUtil.genVdcProperties());
    }

    /**
     * Check if VDC configuration is different in the database vs. what is stored locally
     *
     * @return
     */
    private boolean vdcPropertiesChanged() {
        long localVdcConfigVersion = localVdcPropInfo.getProperty(VdcConfigUtil.VDC_CONFIG_VERSION) == null ? 0 :
                Long.parseLong(localVdcPropInfo.getProperty(VdcConfigUtil.VDC_CONFIG_VERSION));
        long targetVdcConfigVersion = targetSiteInfo.getVdcConfigVersion();

        return localVdcConfigVersion != targetVdcConfigVersion;
    }

    /**
     * Update vdc properties and reboot the node if
     *
     * @param svcId node service id
     * @throws Exception
     */
    private void updateVdcProperties(String svcId) throws Exception {
        // If the change is being done to create a multi VDC configuration or to reduce to a
        // multi VDC configuration a reboot is needed. If only operating on a single VDC
        // do not reboot the nodes.
        String action = targetSiteInfo.getActionRequired();
        if (targetVdcPropInfo.getProperty(VdcConfigUtil.VDC_IDS).contains(",")
                || localVdcPropInfo.getProperty(VdcConfigUtil.VDC_IDS).contains(",")) {
            log.info("Step3: Acquiring vdc lock for vdc properties change.");
            if (!getVdcLock(svcId)) {
                retrySleep();
            } else if (!isQuorumMaintained()) {
                try {
                    coordinator.releasePersistentLock(svcId, vdcLockId);
                } catch (Exception e) {
                    log.error("Failed to release the vdc lock:", e);
                }
                retrySleep();
            } else {
                log.info("Step3: Setting vdc properties and reboot");
                localRepository.setVdcPropertyInfo(targetVdcPropInfo);
                reboot();
            }
            return;
        }

        log.info("Step3: Setting vdc properties not rebooting for single VDC change");

        switch (action) {
            case SiteInfo.RECONFIG_RESTART:
                reconfigRestartSvcs();
                break;
            case SiteInfo.PAUSE_STANDBY:
                log.info("Step3: Acquiring vdc lock for strategy options change.");
                if (getVdcLock(svcId)) {
                    try {
                        targetSiteInfo = coordinator.getTargetInfo(SiteInfo.class);
                        if (!targetSiteInfo.getActionRequired().equals(SiteInfo.PAUSE_STANDBY)) {
                            log.info("strategy options already changed. Do nothing");
                            return;
                        }
                        
                        if (!isQuorumMaintained()) {
                            return;
                        }
                        // restart dbsvc and geodbsvc to exclude the paused site from strategy options
                        localRepository.restart("dbsvc");
                        localRepository.restart("geodbsvc");

                        CoordinatorClient coordinatorClient = coordinator.getCoordinatorClient();
                        SiteInfo currentSiteInfo = coordinatorClient.getTargetInfo(coordinatorClient.getSiteId(),
                                SiteInfo.class);
                        SiteInfo siteInfo = new SiteInfo(System.currentTimeMillis(), SiteInfo.RECONFIG_RESTART,
                                    currentSiteInfo.getTargetDataRevision());
                        coordinatorClient.setTargetInfo(coordinatorClient.getSiteId(), siteInfo);
                        log.info("VDC target version updated to {} for local site", siteInfo.getVdcConfigVersion());
                    } finally {
                        coordinator.releasePersistentLock(svcId, vdcLockId);
                    }
                }
                break;
            default:
                localRepository.setVdcPropertyInfo(targetVdcPropInfo);
        }
    }

    private void reconfigRestartSvcs() throws Exception {
        PropertyInfoExt vdcProperty = new PropertyInfoExt(targetVdcPropInfo.getAllProperties());
        // set the vdc_config_version to an invalid value so that it always gets retried on failure.
        vdcProperty.addProperty(VdcConfigUtil.VDC_CONFIG_VERSION, "-1");
        localRepository.setVdcPropertyInfo(vdcProperty);

        localRepository.reconfigProperties("firewall");
        localRepository.reload("firewall");

        // Reconfigure ZK
        localRepository.reconfigProperties("coordinator");
        localRepository.restart("coordinatorsvc");

        log.info("The ZK dynamic reconfig success");

        localRepository.reconfigProperties("db");
        //localRepository.restart("dbsvc");

        localRepository.reconfigProperties("geodb");
        //localRepository.restart("geodbsvc");

        log.info("Step2: Updating the hash code for local vdc properties");
        vdcProperty.addProperty(VdcConfigUtil.VDC_CONFIG_VERSION, String.valueOf(targetSiteInfo.getVdcConfigVersion()));
        localRepository.setVdcPropertyInfo(vdcProperty);
    }

    private List<String> getJoiningZKNodes() {
        return getStandbyNodeIPAddresses(targetVdcPropInfo);
    }

    private List<String> getStandbyNodeIPAddresses (PropertyInfoExt propertyInfo) {
        Set<Map.Entry<String, String>> properties = propertyInfo.getAllProperties().entrySet();

        // key=server ID e.g. 1, 2 ..
        // value = IPv4 address | [ IPv6 address]
        Map<Integer, String> ipaddresses = new HashMap<>();

        String myVdcId = propertyInfo.getProperty(Constants.MY_VDC_ID_KEY);
        String nodeCountProperty=String.format(Constants.VDC_NODECOUNT_KEY_TEMPLATE, myVdcId);

        int startCount = Integer.valueOf(propertyInfo.getProperty(nodeCountProperty));

        for (Map.Entry<String, String> property: properties) {
            String key = property.getKey();

            // we are only interested IPv4/IPv6 address of standby node
            if (isStandByIPAddressKey(key)) {
                String ipAddr = formalizeIPAddress(property.getValue());
                int serverId = getStandbyServerId(key);

                if (isIPv6Address(ipAddr) && ipaddresses.get(serverId) != null) {
                    // IPv4 address has already been found
                    // ignore the IPv6 address
                    continue;
                }

                if (ipAddr != null && !ipAddr.isEmpty()) {
                    ipaddresses.put(serverId, ipAddr);
                }
            }
        }

        List<String> servers = new ArrayList<>(ipaddresses.size());

        for (Map.Entry<Integer, String> entry : ipaddresses.entrySet()) {
            int serverId = startCount+entry.getKey();
            StringBuilder builder = new StringBuilder(Constants.ZK_SERVER_CONFIG_PREFIX);
            builder.append(serverId);
            builder.append("=");
            builder.append(entry.getValue()); // IP address
            builder.append(Constants.ZK_OBSERVER_CONFIG_SUFFIX);
            servers.add(builder.toString());
        }

        return servers;
    }

    private boolean isStandByIPAddressKey(String key) {
        String myVdcId = targetVdcPropInfo.getProperty(Constants.MY_VDC_ID_KEY);
        return key.contains(myVdcId) && key.matches(Constants.STANDBY_PROPERTY_REGEX);
    }

    private boolean isIPv6Address(String addr) {
        return (addr != null) && addr.contains(":");
    }

    // enclose IPv6 address with '[' and ']'
    private String formalizeIPAddress(String ipAddress) {
        if (isIPv6Address(ipAddress)) {
            return "["+ipAddress+"]"; //IPv6 address
        }

        return ipAddress; //IPv4 address
    }

    // the property name is like vdc_${vdcId}_standby_network_${serverId}_ipaddr[6]
    // if we split it by '_', the [4] element is the server id
    private int getStandbyServerId(String ipaddrPropertyName) {
        String[] subStrs = ipaddrPropertyName.split("_");

        return Integer.valueOf(subStrs[4]);
    }

    /**
     * Try to acquire the vdc lock, like upgrade lock, this also requires rolling reboot
     * so upgrade lock should be acquired at the same time
     *
     * @param svcId
     * @return
     */
    private boolean getVdcLock(String svcId) {
        if (!coordinator.getPersistentLock(svcId, vdcLockId)) {
            log.info("Acquiring vdc lock failed. Retrying...");
            return false;
        }

        if (!coordinator.getPersistentLock(svcId, upgradeLockId)) {
            log.info("Acquiring upgrade lock failed. Retrying...");
            return false;
        }

        // release the upgrade lock
        try {
            coordinator.releasePersistentLock(svcId, upgradeLockId);
        } catch (Exception e) {
            log.error("Failed to release the upgrade lock:", e);
        }
        log.info("Successfully acquired the vdc lock.");
        return true;
    }

    /**
     * If target poweroff state is not NONE, that means user has set it to STARTED.
     * in the checkAllNodesAgreeToPowerOff, all nodes, including control nodes and data nodes
     * will start to publish their poweroff state in the order of [NOTICED, ACKNOWLEDGED, POWEROFF].
     * Every node can publish the next state only if it sees the previous state are found on every other nodes.
     * By doing this, we can gaurantee that all nodes receive the acknowledgement of powering among each other,
     * we can then safely poweroff.
     * No matter the poweroff failed or not, at the end, we reset the target poweroff state back to NONE.
     * CTRL-11690: the new behavior is if an agreement cannot be reached, a best-effort attempt to poweroff the
     * remaining nodes will be made, as if the force parameter is provided.
     */
    private void gracefulPoweroffCluster() {
        if (targetPowerOffState != null && targetPowerOffState.getPowerOffState() != PowerOffState.State.NONE) {
            boolean forceSet = targetPowerOffState.getPowerOffState() == PowerOffState.State.FORCESTART;
            log.info("Step2: Trying to reach agreement with timeout on cluster poweroff");
            if (checkAllNodesAgreeToPowerOff(forceSet) && initiatePoweroff(forceSet)) {
                resetTargetPowerOffState();
                poweroffCluster();
            } else {
                log.warn("Step2: Failed to reach agreement among all the nodes. Proceed with best-effort poweroff");
                initiatePoweroff(true);
                resetTargetPowerOffState();
                poweroffCluster();
            }
        }
    }
    
    /**
     * Check if data revision is same as local one. If not, switch to target revision and reboot the whole cluster
     * simultaneously.
     * 
     * The data revision switch is implemented as 2-phase commit protocol to ensure no partial commit
     * 
     * @throws Exception
     */
    private void updateDataRevision() throws Exception {
        String localRevision = localRepository.getDataRevision();
        String targetDataRevision = targetSiteInfo.getTargetDataRevision();
        log.info("Step3: Trying to reach agreement with timeout for data revision change");
        String barrierPath = String.format("%s/%s/DataRevisionBarrier", ZkPath.SITES, coordinator.getCoordinatorClient().getSiteId());
        DistributedDoubleBarrier barrier = coordinator.getCoordinatorClient().getDistributedDoubleBarrier(barrierPath, coordinator.getNodeCount());
        try {
            boolean phase1Agreed = barrier.enter(DATA_REVISION_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (phase1Agreed) {
                // reach phase 1 agreement, we can start write to local property file
                log.info("Step3: Reach phase 1 agreement for data revision change");
                localRepository.setDataRevision(targetDataRevision, false);
                boolean phase2Agreed = barrier.leave(DATA_REVISION_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (phase2Agreed) {
                    // phase 2 agreement is received, we can make sure data revision change is written to local property file
                    log.info("Step3: Reach phase 2 agreement for data revision change");
                    localRepository.setDataRevision(targetDataRevision, true);
                    reboot();
                } else {
                    log.info("Step3: Failed to reach phase 2 agreement. Rollback revision change");
                    localRepository.setDataRevision(localRevision, true);
                }
            } 
            log.warn("Step3: Failed to reach agreement among all the nodes. Delay data revision change until next run");
        } catch (Exception ex) {
            log.warn("Step3. Internal error happens when negotiating data revision change", ex);
        }
    }    
   
    public void poweroffCluster() {
        log.info("powering off the cluster!");
        final String[] cmd = { POWEROFFTOOL_COMMAND };
        Exec.sudo(SHUTDOWN_TIMEOUT_MILLIS, cmd);
    }

}
