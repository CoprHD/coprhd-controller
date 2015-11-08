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

import org.apache.curator.framework.recipes.barriers.DistributedDoubleBarrier;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.zookeeper.ZooKeeper.States;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.PowerOffState;
import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteError;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.util.VdcConfigUtil;
import com.emc.storageos.management.jmx.recovery.DbManagerOps;
import com.emc.storageos.security.ipsec.IPsecConfig;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
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
    private static final int SWITCHOVER_ZK_WRITALE_WAIT_INTERVAL = 1000 * 5;

    private static final int SWITCHOVER_BARRIER_TIMEOUT = 300;

    private static final Logger log = LoggerFactory.getLogger(VdcSiteManager.class);

    private static final String VDC_IDS_KEY = "vdc_ids";
    private static final int VDC_RPOP_BARRIER_TIMEOUT = 5;

    private DbClient dbClient;
    private IPsecConfig ipsecConfig;

    // local and target info properties
    private PropertyInfoExt localVdcPropInfo;
    private PropertyInfoExt targetVdcPropInfo;
    private PowerOffState targetPowerOffState;
    
    private static final String POWEROFFTOOL_COMMAND = "/etc/powerofftool";

    // set to 2.5 minutes since it takes over 2m for ssh to timeout on non-reachable hosts
    private static final long SHUTDOWN_TIMEOUT_MILLIS = 150000;
    // Timeout in minutes for add/resume/data sync
    // If data synchronization takes long than this value, set site to error
    public static final int ADD_STANDBY_TIMEOUT_MILLIS = 20 * 60 * 1000; // 20 minutes
    public static final int RESUME_STANDBY_TIMEOUT_MILLIS = 20 * 60 * 1000; // 20 minutes
    public static final int DATA_SYNC_TIMEOUT_MILLIS = 20 * 60 * 1000; // 20 minutes
    public static final int SWITCHOVER_TIMEOUT_MILLIS = 20 * 60 * 1000; // 20 minutes
    
    // data revision time out - 11 minutes
    private static final long DATA_REVISION_WAIT_TIMEOUT_SECONDS = 300;
    
    private static final String URI_INTERNAL_POWEROFF = "/control/internal/cluster/poweroff";
    
    private static final String LOCK_REMOVE_STANDBY="drRemoveStandbyLock";
    
    private SiteInfo targetSiteInfo;

    private Service service;
    
    private String currentSiteId;
    
    private DrUtil drUtil;
   
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setService(Service svc) {
        this.service = svc;
    }

    public void setIpsecConfig(IPsecConfig ipsecConfig) {
        this.ipsecConfig = ipsecConfig;
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
        currentSiteId = coordinator.getCoordinatorClient().getSiteId();
        drUtil = new DrUtil(coordinator.getCoordinatorClient());
        
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
                log.info("Step1b failed and will be retried:", e);
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

            // Step3: update vdc configuration if changed
            log.info("Step3: If VDC configuration is changed update");
            if (vdcPropertiesChanged()) {
                log.info("Step3: Current vdc properties are not same as target vdc properties. Updating.");
                log.debug("Current local vdc properties: " + localVdcPropInfo);
                log.debug("Target vdc properties: " + targetVdcPropInfo);

                try {
                    updateVdcProperties(svcId);
                    updatePlannedFailoverSiteState();
                } catch (Exception e) {
                    log.info("Step3: VDC properties update failed and will be retried:", e);
                    // Restart the loop immediately so that we release the upgrade lock.
                    continue;
                }
                continue;
            }

            // Step4: change data revision
            String targetDataRevision = targetSiteInfo.getTargetDataRevision();
            log.info("Step4: check if target data revision is changed - {}", targetDataRevision);
            try {
                String localRevision = localRepository.getDataRevision();
                log.info("Step4: local data revision is {}", localRevision);
                if (!targetSiteInfo.isNullTargetDataRevision() && !targetDataRevision.equals(localRevision)) {
                    updateDataRevision();
                    continue;
                }
            } catch (Exception e) {
                log.error("Step4: Failed to update data revision. {}", e);
                continue;
            }

            // Step5: set site error state if on primary
            try {
                updateSiteErrors();
            } catch (RuntimeException e) {
                log.error("Step5: Failed to set site errors. {}", e);
                continue;
            }

            // Step6: sleep
            log.info("Step6: sleep");
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
        // ipsec key is a vdc property as well and saved in ZK.
        // targetVdcPropInfo = loadVdcConfigFromDatabase();
        targetVdcPropInfo = loadVdcConfig();

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
     * Load the vdc configurations
     * @return
     * @throws Exception
     */
    private PropertyInfoExt loadVdcConfig() throws Exception {
        targetVdcPropInfo = loadVdcConfigFromDatabase();
        targetVdcPropInfo.addProperty("ipsec_key", ipsecConfig.getPreSharedKey());
        return targetVdcPropInfo;
    }

    /**
     * Load the vdc vonfiguration from the database
     *
     * @return
     */
    private PropertyInfoExt loadVdcConfigFromDatabase() {
        VdcConfigUtil vdcConfigUtil = new VdcConfigUtil();
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

        log.info("Step3: Setting vdc properties not rebooting for single VDC change, action={}", action);
        checkAndRemoveStandby();

        switch (action) {
            case SiteInfo.RECONFIG_RESTART:
                rebuildLocalDbIfNecessary();
                reconfigRestartSvcs();
                cleanupSiteErrorIfNecessary();
                break;
            case SiteInfo.RECONFIG_IPSEC: // for ipsec key rotation
                reconfigIPsec();
            default:
                PropertyInfoExt vdcProperty = new PropertyInfoExt(targetVdcPropInfo.getAllProperties());
                vdcProperty.addProperty(VdcConfigUtil.VDC_CONFIG_VERSION,
                        String.valueOf(targetSiteInfo.getVdcConfigVersion()));
                localRepository.setVdcPropertyInfo(vdcProperty);
        }
    }

    /**
     * Reconfig IPsec when vdc properties (key, IPs or both) get changed.
     * @throws Exception
     */
    private void reconfigIPsec() throws Exception {
        updateVdcPropertiesAndWaitForAll();
        reconfigAndRestartIPsec();
        finishUpdateVdcProperties();
    }

    /**
     * regenerate ipsec configuration files and restart service
     */
    private void reconfigAndRestartIPsec() {
        localRepository.reconfigProperties("ipsec");
        localRepository.reload("ipsec");
    }

    /**
     * update vdc properties from zk to disk and wait for all nodes are done via barrier
     */
    private void updateVdcPropertiesAndWaitForAll() throws Exception {
        DistributedDoubleBarrier barrier = enterBarrier("VdcPropBarrier", VDC_RPOP_BARRIER_TIMEOUT);

        PropertyInfoExt vdcProperty = new PropertyInfoExt(targetVdcPropInfo.getAllProperties());
        // set the vdc_config_version to an invalid value so that it always gets retried on failure.
        vdcProperty.addProperty(VdcConfigUtil.VDC_CONFIG_VERSION, "-1");
        localRepository.setVdcPropertyInfo(vdcProperty);

        leaveBarrier(barrier);
    }

    /**
     * Finish vdc properties update by saving a real vdc config version.
     */
    private void finishUpdateVdcProperties() {
        log.info("Setting the real config version for local vdc properties");
        PropertyInfoExt vdcProperty = new PropertyInfoExt(targetVdcPropInfo.getAllProperties());
        vdcProperty.addProperty(VdcConfigUtil.VDC_CONFIG_VERSION, String.valueOf(targetSiteInfo.getVdcConfigVersion()));
        localRepository.setVdcPropertyInfo(vdcProperty);
    }
    
    private DistributedDoubleBarrier enterBarrier(String path, int timeout) throws Exception {
        return enterBarrier(path, timeout, getChildrenCountOnBarrier());
    }

    /**
     * Waiting for all nodes entering the VdcPropBarrier.
     * @return
     * @throws Exception
     */
    private DistributedDoubleBarrier enterBarrier(String path, int timeout, int memberQty) throws Exception {
        log.info("Waiting for all nodes entering VdcPropBarrier");

        // key rotation is always done on primary site. when adding standby this is done on both site.
        String barrierPath = String.format("%s/%s/&s", ZkPath.SITES, coordinator.getCoordinatorClient().getSiteId(), path);

        // the children # should be the node # in entire VDC. before linking together, it's # in a site.
        DistributedDoubleBarrier barrier = coordinator.getCoordinatorClient().getDistributedDoubleBarrier(barrierPath, memberQty);

        boolean allEntered = barrier.enter(timeout, TimeUnit.SECONDS);
        if (allEntered) {
            log.info("All nodes entered VdcPropBarrier");
            return barrier;
        } else {
            throw new Exception(String.format("Only Part of nodes entered within %s seconds, Skip updating", timeout));
        }
    }

    /**
     * Get the number of nodes should involve the barrier. It's all nodes of a site when adding standby while nodes of a VDC when rotating key.
     * @return
     */
    private int getChildrenCountOnBarrier() {
        SiteInfo.ActionScope scope = targetSiteInfo.getActionScope();
        switch (scope) {
            case SITE:
                return coordinator.getNodeCount();
            case VDC:
                // TODO: need a method to return the # of VDC
                throw new NotImplementedException();
            default:
                throw new RuntimeException("Unknown Action Scope is set in SiteInfo: " + scope);
        }
    }

    /**
     * Waiting for all nodes leaving the VdcPropBarrier.
     * @param barrier
     * @throws Exception
     */
    private void leaveBarrier(DistributedDoubleBarrier barrier) throws Exception {
        // Even if part of nodes fail to leave this barrier within timeout, we still let it pass. The ipsec monitor will handle failure on other nodes.
        log.info("Waiting for all nodes leaving VdcPropBarrier");

        boolean allLeft = barrier.leave(VDC_RPOP_BARRIER_TIMEOUT, TimeUnit.SECONDS);
        if (allLeft) {
            log.info("All nodes left VdcPropBarrier");
        } else {
            log.warn("Only Part of nodes left VdcPropBarrier before timeout");
        }
    }

    private void reconfigRestartSvcs() throws Exception {
        Site site = drUtil.getSite(drUtil.getCoordinator().getSiteId());
        log.info("Site: {}", site.toString());
        
        updateVdcPropertiesAndWaitForAll();

        reconfigAndRestartIPsec();

        localRepository.reconfigProperties("firewall");
        localRepository.reload("firewall");
            
        reconfigAndRestartCoordinator(site);
        
        finishUpdateVdcProperties();
    }

    private void reconfigAndRestartCoordinator(Site site) throws Exception {
        // Reconfigure ZK
        // TODO: think again how to make use of the dynamic zookeeper configuration
        // The previous approach disconnects all the clients, no different than a service restart.
        
        if (site.getState().equals(SiteState.PRIMARY_SWITCHING_OVER) || site.getState().equals(SiteState.STANDBY_SWITCHING_OVER)) {
            log.info("Wait for barrier to reconfig/restart coordinator when switchover");
            DistributedDoubleBarrier barrier = enterBarrier(Constants.SWITCHOVER_BARRIER, SWITCHOVER_BARRIER_TIMEOUT, getSwitchoverNodeCount());
            
            localRepository.reconfigProperties("coordinator");
            
            barrier.leave();
            
            localRepository.restart("coordinatorsvc");
        } else {
            localRepository.reconfigProperties("coordinator");
            localRepository.restart("coordinatorsvc");
        }
    }

    private List<String> getJoiningZKNodes() {
        return getStandbyNodeIPAddresses(targetVdcPropInfo);
    }

    private List<String> getStandbyNodeIPAddresses(PropertyInfoExt propertyInfo) {
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

    /**
     * Check if a standby is removing from an ensemble. 
     * 
     * @return
     */
    private boolean hasRemovingStandby() {
        return !listRemovingStandby().isEmpty();
    }
    
    private List<Site> listRemovingStandby() {
        List<Site> result = new ArrayList<Site>();
        for(Site site : drUtil.listSites()) {
            if (site.getState().equals(SiteState.STANDBY_REMOVING)) {
                result.add(site);
            }
        }
        return result;
    }
    
    private boolean isRemovingCurrentSite(List<Site> toBeRemovedSites) {
        for(Site site : toBeRemovedSites) {
            if (site.getState().equals(SiteState.STANDBY_REMOVING)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if we are removing a standby. If yes, remove a standby site from current ensemble. 
     * 
     * @throws Exception
     */
    private void checkAndRemoveStandby() throws Exception{
        if (drUtil.isPrimary()) {
            checkAndRemoveOnPrimary();
        } else {
            checkAndRemoveOnStandby();
        }
    }

    private void checkAndRemoveOnPrimary() throws Exception {
        String svcId = coordinator.getMySvcId();
        
        InterProcessLock lock = coordinator.getCoordinatorClient().getLock(LOCK_REMOVE_STANDBY);
        while (hasRemovingStandby()) {
            log.info("Accquiring lock {}", LOCK_REMOVE_STANDBY); 
            lock.acquire();
            log.info("Accquired lock {}", LOCK_REMOVE_STANDBY); 
            List<Site> toBeRemovedSites = listRemovingStandby();
            try {
                    
                for (Site site : toBeRemovedSites) {
                    try {
                        removeDbNodes(site);
                    } catch (Exception e) { 
                        populateStandbySiteErrorIfNecessary(site, APIException.internalServerErrors.removeStandbyReconfigFailed(e.getMessage()));
                        throw e;
                    }
                }
                for (Site site : toBeRemovedSites) {
                    try {
                        removeDbReplication(site);
                    } catch (Exception e) { 
                        populateStandbySiteErrorIfNecessary(site, APIException.internalServerErrors.removeStandbyReconfigFailed(e.getMessage()));
                        throw e;
                    }
                }
            }  finally {
                lock.release();
                log.info("Release lock {}", LOCK_REMOVE_STANDBY);   
            }
        }
    }
    
    private void checkAndRemoveOnStandby() {
        List<Site> toBeRemovedSites = listRemovingStandby();
        if (isRemovingCurrentSite(toBeRemovedSites)) {
            log.info("Current standby site is removed from DR. You can power it on and promote it as primary later");
            return;
        } else {
            log.info("Waiting for completion of site removal from primary site");
            while (hasRemovingStandby()) {
                log.info("Waiting for completion of site removal from primary site");
                retrySleep();
            }
        }
    }
    
    private void removeDbNodes(Site site) throws Exception {
        poweroffRemoteSite(site);
        
        String dcName = drUtil.getCassandraDcId(site);
        DbManagerOps dbOps = new DbManagerOps(Constants.DBSVC_NAME);
        try {
            dbOps.removeDataCenter(dcName);
        } finally {
            dbOps.close();
        }
        
        DbManagerOps geodbOps = new DbManagerOps(Constants.GEODBSVC_NAME);
        try {
            geodbOps.removeDataCenter(dcName);
        } finally {
            geodbOps.close();
        }
    }
    
    private void removeDbReplication(Site site) {
        CoordinatorClient coordinatorClient = coordinator.getCoordinatorClient();
        String dcName = drUtil.getCassandraDcId(site);
        ((DbClientImpl)dbClient).getLocalContext().removeDcFromStrategyOptions(dcName);
        ((DbClientImpl)dbClient).getGeoContext().removeDcFromStrategyOptions(dcName);
        coordinatorClient.removeServiceConfiguration(site.toConfiguration());
        log.info("Removed site {} configuration from ZK", site.getUuid());
    }

    private void rebuildLocalDbIfNecessary() throws Exception {
        CoordinatorClient coordinatorClient = coordinator.getCoordinatorClient();
        Configuration localSiteConfig = coordinatorClient.queryConfiguration(Site.CONFIG_KIND,
                coordinatorClient.getSiteId());
        Site localSite = new Site(localSiteConfig);

        String svcId = coordinator.getMySvcId();
        while (localSite.getState().equals(SiteState.STANDBY_RESUMING)) {
            if (!getVdcLock(svcId)) {
                retrySleep(); // retry until we get the lock
                localSiteConfig = coordinatorClient.queryConfiguration(Site.CONFIG_KIND,
                        coordinatorClient.getSiteId());
                localSite = new Site(localSiteConfig);
                continue;
            }

            try {
                int nodeCount = localSite.getNodeCount();

                // add back the paused site from strategy options of dbsvc and geodbsvc
                String dcId = drUtil.getCassandraDcId(localSite);
                ((DbClientImpl) dbClient).getLocalContext().addDcToStrategyOptions(dcId, nodeCount);
                ((DbClientImpl) dbClient).getGeoContext().addDcToStrategyOptions(dcId, nodeCount);

                localSite.setState(SiteState.STANDBY_SYNCING);
                coordinatorClient.persistServiceConfiguration(localSite.toConfiguration());
            } finally {
                coordinator.releasePersistentLock(svcId, vdcLockId);
            }
        }

        // restart db services to initiate the rebuild
        if (localSite.getState().equals(SiteState.STANDBY_SYNCING)) {
            localRepository.restart("dbsvc");
            localRepository.restart("geodbsvc");
        }
    }

    private void poweroffRemoteSite(Site site) {
        String siteId = site.getUuid();
        if (!drUtil.isSiteUp(siteId)) {
            log.info("Site {} is down. no need to poweroff it", site.getUuid());
            return;
        }
        // all syssvc shares same port
        String baseNodeURL = String.format(SysClientFactory.BASE_URL_FORMAT, site.getVip(), service.getEndpoint().getPort());
        SysClientFactory.getSysClient(URI.create(baseNodeURL)).post(URI.create(URI_INTERNAL_POWEROFF), null, null);
        log.info("Powering off site {}", siteId);
        while(drUtil.isSiteUp(siteId)) {
            log.info("Short sleep and will check site status later");
            retrySleep();
        }
    }

    private void populateStandbySiteErrorIfNecessary(Exception e) {
        for (Site site : drUtil.listSites()) {
            if (site.getUuid().equals(currentSiteId)) {
                if (site.getState().equals(SiteState.STANDBY_REMOVING)) {
                    populateStandbySiteErrorIfNecessary(site, APIException.internalServerErrors.removeStandbyReconfigFailed(e.getMessage()));
                }
                
                break;
            }
        }
    }
    
    private void populateStandbySiteErrorIfNecessary(Site site, InternalServerErrorException e) {
        SiteError error = new SiteError(e);
        
        log.info("Set error state for site: {}", site.getUuid());
        coordinator.getCoordinatorClient().setTargetInfo(site.getUuid(),  error);

        site.setState(SiteState.STANDBY_ERROR);
        coordinator.getCoordinatorClient().persistServiceConfiguration(site.getUuid(), site.toConfiguration());
    }
    
    private void cleanupSiteErrorIfNecessary() {
        String siteId = coordinator.getCoordinatorClient().getSiteId();
        
        Configuration config = coordinator.getCoordinatorClient().queryConfiguration(Site.CONFIG_KIND, siteId);
        Site site = new Site(config);
        
        log.info("site: {}", site.toString());
        
        if (site.getState().equals(SiteState.STANDBY_REMOVING)) {
            log.info("Cleanup site error");
            SiteError siteError = coordinator.getCoordinatorClient().getTargetInfo(siteId, SiteError.class);
            if (siteError != null) {
                siteError.cleanup();
                coordinator.getCoordinatorClient().setTargetInfo(siteId, siteError);
            }
        }
    }

    private void updateSiteErrors() {
        CoordinatorClient coordinatorClient = coordinator.getCoordinatorClient();

        if (!drUtil.isPrimary()) {
            log.info("Step5: current site is a standby, nothing to do");
            return;
        }

        for(Site site : drUtil.listSites()) {
            SiteError error = getSiteError(site);
            if (error != null) {
                coordinatorClient.setTargetInfo(site.getUuid(), error);

                site.setState(SiteState.STANDBY_ERROR);
                coordinatorClient.persistServiceConfiguration(site.getUuid(), site.toConfiguration());
            }
        }
    }

    private SiteError getSiteError(Site site) {
        SiteError error = null;
        SiteInfo targetSiteInfo = coordinator.getCoordinatorClient().getTargetInfo(site.getUuid(), SiteInfo.class);
        long lastSiteUpdateTime = targetSiteInfo.getVdcConfigVersion();
        long currentTime = System.currentTimeMillis();

        switch(site.getState()) {
            case STANDBY_ADDING:
                if (currentTime - lastSiteUpdateTime > ADD_STANDBY_TIMEOUT_MILLIS) {
                    log.info("Step5: Site {} set to error due to add standby timeout", site.getName());
                    error = new SiteError(APIException.internalServerErrors.addStandbyFailedTimeout(
                            ADD_STANDBY_TIMEOUT_MILLIS / 60 / 1000));
                }
                break;
            case STANDBY_RESUMING:
                if (currentTime - lastSiteUpdateTime > RESUME_STANDBY_TIMEOUT_MILLIS) {
                    log.info("Step5: Site {} set to error due to resume standby timeout", site.getName());
                    error = new SiteError(APIException.internalServerErrors.resumeStandbyFailedTimeout(
                            RESUME_STANDBY_TIMEOUT_MILLIS / 60 / 1000));
                }
                break;
            case STANDBY_SYNCING:
                if (currentTime - lastSiteUpdateTime > DATA_SYNC_TIMEOUT_MILLIS) {
                    log.info("Step5: Site {} set to error due to data sync timeout", site.getName());
                    error = new SiteError(APIException.internalServerErrors.dataSyncFailedTimeout(
                            DATA_SYNC_TIMEOUT_MILLIS / 60 / 1000));
                }
                break;
            case PRIMARY_SWITCHING_OVER:
                if (currentTime - lastSiteUpdateTime > SWITCHOVER_TIMEOUT_MILLIS) {
                    log.info("Step5: site {} set to error due to switchover timeout", site.getName());
                    error = new SiteError(APIException.internalServerErrors.switchoverPrimaryFailedTimeout(
                            site.getUuid(), DATA_SYNC_TIMEOUT_MILLIS / 60 / 1000));
                }
                break;
            case STANDBY_SWITCHING_OVER:
                if (currentTime - lastSiteUpdateTime > SWITCHOVER_TIMEOUT_MILLIS) {
                    log.info("Step5: site {} set to error due to switchover timeout", site.getName());
                    error = new SiteError(APIException.internalServerErrors.switchoverStandbyFailedTimeout(
                            site.getUuid(), DATA_SYNC_TIMEOUT_MILLIS / 60 / 1000));
                }
                break;
        }
        return error;
    }
    
    /**
     * This API will handle the switchover (planned failover) for both new/old primary site
     * @throws Exception
     */
    private void updatePlannedFailoverSiteState() throws Exception {
        String siteId = coordinator.getCoordinatorClient().getSiteId();
        Site site = drUtil.getSite(siteId);  
        
        log.info("site: {}", site.toString());
        
        // old primary
        if (site.getState().equals(SiteState.PRIMARY_SWITCHING_OVER)) {
            proccessOldPrimarySiteSwitchover(site);
        }
        
        // new primary
        if (site.getState().equals(SiteState.STANDBY_SWITCHING_OVER)) {
            proccessNewPrimarySiteSwitchover(site);
        }
    }

    private void proccessNewPrimarySiteSwitchover(Site site) throws Exception {
        log.info("This is planned failover standby site (new primary)");
        
        blockUntilZookeeperIsWritableConnected();
        
        DistributedDoubleBarrier barrier = enterBarrier(Constants.SWITCHOVER_STANDBY_BARRIER, SWITCHOVER_BARRIER_TIMEOUT);
        
        log.info("Set state to PRIMARY");
        site.setState(SiteState.PRIMARY);
        coordinator.getCoordinatorClient().persistServiceConfiguration(site.toConfiguration());
        
        barrier.leave();
        
        log.info("Reboot this node after planned failover");
        localRepository.reboot();
    }

    private void proccessOldPrimarySiteSwitchover(Site site) throws Exception {
        log.info("This is planned failover primary site (old primrary)");
        
        blockUntilZookeeperIsWritableConnected();
        
        DistributedDoubleBarrier barrier = enterBarrier(Constants.SWITCHOVER_PRIMARY_BARRIER, SWITCHOVER_BARRIER_TIMEOUT);
        
        site.setState(SiteState.STANDBY_SYNCED);
        coordinator.getCoordinatorClient().persistServiceConfiguration(site.toConfiguration());
        
        barrier.leave();
        
        log.info("Reboot this node after planned failover");
        localRepository.reboot();
    }
    
    private void blockUntilZookeeperIsWritableConnected() {
        while (true) {
            try {
                States state = ((CoordinatorClientImpl)coordinator.getCoordinatorClient()).getZkConnection().curator().getZookeeperClient().getZooKeeper().getState();
                
                if (state.equals(States.CONNECTED))
                    return;
            } catch (Exception e) {
                log.error("Can't get Zk state {}", e);
            } 
            
            try {
                Thread.sleep(SWITCHOVER_ZK_WRITALE_WAIT_INTERVAL);
            } catch (InterruptedException e) {
                //Ingore
            };
        }
    }
    
    private int getSwitchoverNodeCount() {
        int count = 0;
        
        for (Site site : drUtil.listSites()) {
            if (site.getState().equals(SiteState.PRIMARY_SWITCHING_OVER) || site.getState().equals(SiteState.STANDBY_SWITCHING_OVER)) {
                count += site.getNodeCount();
            }
        }
        
        log.info("Node count is switchover is {}", count);
        return count;
    }
}
