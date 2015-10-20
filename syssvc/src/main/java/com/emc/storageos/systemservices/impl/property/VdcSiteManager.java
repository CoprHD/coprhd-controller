/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.property;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.curator.framework.recipes.barriers.DistributedDoubleBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.PowerOffState;
import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.util.VdcConfigUtil;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.management.jmx.recovery.DbManagerOps;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
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
    private String defaultPSKFile;

    // local and target info properties
    private PropertyInfoExt localVdcPropInfo;
    private PropertyInfoExt targetVdcPropInfo;
    private PowerOffState targetPowerOffState;
    
    private static final String POWEROFFTOOL_COMMAND = "/etc/powerofftool";

    // set to 2.5 minutes since it takes over 2m for ssh to timeout on non-reachable hosts
    private static final long SHUTDOWN_TIMEOUT_MILLIS = 150000;
    
    // data revision time out - 11 minutes
    private static final long DATA_REVISION_WAIT_TIMEOUT_SECONDS = 300;
    
    private static final String URI_INTERNAL_POWEROFF = "/control/internal/cluster/poweroff";
    
    private SiteInfo targetSiteInfo;

    private Service service;
    
    private String currentSiteId;
    
    private VirtualDataCenter localVdc;
   
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setService(Service svc) {
        this.service = svc;
    }

    public void setDefaultPSKFile(String defaultPSKFile) {
        this.defaultPSKFile = defaultPSKFile;
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
        localVdc = VdcUtil.getLocalVdc();
                
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
                    log.info("Step2: VDC properties update failed and will be retried:", e);
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

    private PropertyInfoExt loadVdcConfig() throws Exception {
        targetVdcPropInfo = loadVdcConfigFromDatabase();
        // ipsec config is stored in zk
        targetVdcPropInfo = loadVdcConfigFromZK();

        return targetVdcPropInfo;
    }

    private PropertyInfoExt loadVdcConfigFromZK() throws Exception {
        // assuming targetVdcPropInfo is not null;

        String ipsecKey = null;
        Configuration ipsecConfig = coordinator.getCoordinatorClient().queryConfiguration("ipsec", "ipsecConfig");
        if (ipsecConfig == null) {
            ipsecKey = loadDefaultIpsecKeyFromFile();
        } else {
            ipsecKey = ipsecConfig.getConfig("ipsec_key");
        }

        targetVdcPropInfo.addProperty("ipsec_key", ipsecKey);
        return targetVdcPropInfo;
    }

    private String loadDefaultIpsecKeyFromFile() throws Exception {
        BufferedReader in = new BufferedReader(new FileReader(new File(defaultPSKFile)));
        try {
            String key = in.readLine();
            return key;
        } finally {
            in.close();
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
                checkAndRemoveStandby();
                reconfigRestartSvcs();
                break;
            case SiteInfo.RECONFIG_IPSEC: // for ipsec key rotation
                reconfigIPsec();
            default:
                localRepository.setVdcPropertyInfo(targetVdcPropInfo);
        }
    }

    private void reconfigIPsec() throws Exception {
        updateVdcPropertiesAndWaitForAll();
        reconfigAndRestartIPsec();
        finishUpdateVdcProperties();
    }

    private void reconfigAndRestartIPsec() {
        //localRepository.reconfigProperties("ipsec");
        //localRepository.restart("ipsec");
    }

    /**
     * update vdc properties from zk to disk and wait for all nodes are done via barrier
     */
    private void updateVdcPropertiesAndWaitForAll() throws Exception {
        DistributedDoubleBarrier barrier = enterBarrier();

        PropertyInfoExt vdcProperty = new PropertyInfoExt(targetVdcPropInfo.getAllProperties());
        // set the vdc_config_version to an invalid value so that it always gets retried on failure.
        vdcProperty.addProperty(VdcConfigUtil.VDC_CONFIG_VERSION, "-1");
        localRepository.setVdcPropertyInfo(vdcProperty);

        leaveBarrier(barrier);
    }

    private void finishUpdateVdcProperties() {
        log.info("Setting the real config version for local vdc properties");
        PropertyInfoExt vdcProperty = new PropertyInfoExt(targetVdcPropInfo.getAllProperties());
        vdcProperty.addProperty(VdcConfigUtil.VDC_CONFIG_VERSION, String.valueOf(targetSiteInfo.getVdcConfigVersion()));
        localRepository.setVdcPropertyInfo(vdcProperty);
    }

    private DistributedDoubleBarrier enterBarrier() throws Exception {
        log.info("Waiting for all nodes entering VdcPropBarrier");

        // key rotation is always done on primary site. when adding standby this is done on both site.
        String barrierPath = String.format("%s/%s/VdcPropBarrier", ZkPath.SITES, coordinator.getCoordinatorClient().getSiteId());

        // the children # should be the node # in entire VDC. before linking together, it's # in a site.
        DistributedDoubleBarrier barrier = coordinator.getCoordinatorClient().getDistributedDoubleBarrier(barrierPath, getChildrenCountOnBarrier());

        boolean allEntered = barrier.enter(5, TimeUnit.SECONDS);
        if (allEntered) {
            log.info("All nodes entered VdcPropBarrier");
            return barrier;
        } else {
            throw new Exception("Only Part of nodes entered within 5 seconds, Skip updating");
        }
    }

    private int getChildrenCountOnBarrier() {
        SiteInfo.ActionScope scope = targetSiteInfo.getActionScope();
        switch (scope) {
            case SITE:
                return coordinator.getNodeCount();
            case VDC:
                // TODO: need a method to return the # of VDC
                return 2*coordinator.getNodeCount();
            default:
                throw new RuntimeException("");
        }
    }

    private void leaveBarrier(DistributedDoubleBarrier barrier) throws Exception {
        // Even if part of nodes fail to leave this barrier within timeout, we still let it pass. The ipsec monitor will handle failure on other nodes.
        log.info("Waiting for all nodes leaving VdcPropBarrier");

        boolean allLeft = barrier.leave(5, TimeUnit.SECONDS);
        if (allLeft) {
            log.info("All nodes left VdcPropBarrier");
        } else {
            log.info("Only Part of nodes left VdcPropBarrier before timeout");
        }
    }

    /**
     * Generate Cassandra data center name for given site. 
     * 
     * @param site
     * @return
     */
    private String getCassandraDcId(Site site) {
        if (site.getState().equals(SiteState.PRIMARY)) {
            return localVdc.getShortId();
        } else {
            return String.format("%s-%s", localVdc.getShortId(), site.getStandbyShortId());
        }
    }

    private void reconfigRestartSvcs() throws Exception {

        updateVdcPropertiesAndWaitForAll();

        reconfigAndRestartIPsec();

        localRepository.reconfigProperties("firewall");
        localRepository.reload("firewall");

        // Reconfigure ZK
        // TODO: think again how to make use of the dynamic zookeeper configuration
        // The previous approach disconnects all the clients, no different than a service restart.
        localRepository.reconfigProperties("coordinator");
        localRepository.restart("coordinatorsvc");

        localRepository.reconfigProperties("db");
        //localRepository.restart("dbsvc");

        localRepository.reconfigProperties("geodb");
        //localRepository.restart("geodbsvc");

        finishUpdateVdcProperties();
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

    /**
     * Check if a standby is removing from an ensemble. 
     * 
     * @return
     */
    private boolean isRemovingStandby() {
        List<Site> sites = listSites(localVdc);

        for(Site site : sites) {
            if (site.getState().equals(SiteState.STANDBY_REMOVING)) {
                if (!currentSiteId.equals(site.getUuid())) {
                    return true;
                }
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
        String svcId = coordinator.getMySvcId();
        String primarySiteId = coordinator.getCoordinatorClient().getPrimarySiteId();
        
        while (isRemovingStandby()) {
            if (!primarySiteId.equals(currentSiteId)) {
                log.info("Waiting for completion of site removal from primary site");
                retrySleep();
                continue;
            }
            
            if (!getVdcLock(svcId)) {
                retrySleep(); // retry until we get the lock
                continue;
            }
            
            try {
                List<Site> sites = listSites(localVdc);
                for(Site site : sites) {
                    if (!site.getState().equals(SiteState.STANDBY_REMOVING)) {
                        continue;
                    }
                    if (currentSiteId.equals(site.getUuid())) {
                        log.info("Current site is removed from a DR. It could be manually promoted as primary site");
                    } else {
                        removeSiteFromReplication(site);
                    }
                }
            } finally {
                coordinator.releasePersistentLock(svcId, vdcLockId);
            }
        }
    }

    private void removeSiteFromReplication(Site site) throws Exception {
        CoordinatorClient coordinatorClient = coordinator.getCoordinatorClient();
        
        poweroffRemoteSite(site);
        
        String dcName = getCassandraDcId(site);
        DbManagerOps dbOps = new DbManagerOps(Constants.DBSVC_NAME);
        try {
            dbOps.removeDataCenter(dcName);
        } finally {
            dbOps.close();
        }
        ((DbClientImpl)dbClient).getLocalContext().removeDcFromStrategyOptions(dcName);
        
        DbManagerOps geodbOps = new DbManagerOps(Constants.GEODBSVC_NAME);
        try {
            geodbOps.removeDataCenter(dcName);
        } finally {
            geodbOps.close();
        }
        ((DbClientImpl)dbClient).getGeoContext().removeDcFromStrategyOptions(dcName);
        
        coordinatorClient.removeServiceConfiguration(site.toConfiguration());
        log.info("Removed site {} configuration from ZK", site.getUuid());
    }
    
    private List<Site> listSites(VirtualDataCenter vdc) {
        List<Site> result = new ArrayList<Site>();
        for(Configuration config : coordinator.getCoordinatorClient().queryAllConfiguration(Site.CONFIG_KIND)) {
            Site site = new Site(config);
            if (!vdc.getId().equals(site.getVdc())) {
                continue;
            }
            result.add(site);
        }
        return result;
    }

    private void poweroffRemoteSite(Site site) {
        if (!isSiteUp(site)) {
            log.info("Site {} is down. no need to poweroff it", site.getUuid());
            return;
        }
        // all syssvc shares same port
        String baseNodeURL = String.format(SysClientFactory.BASE_URL_FORMAT, site.getVip(), service.getEndpoint().getPort());
        SysClientFactory.getSysClient(URI.create(baseNodeURL)).post(URI.create(URI_INTERNAL_POWEROFF), null, null);
        log.info("Powering off site {}", site.getUuid());
        while(isSiteUp(site)) {
            log.info("Short sleep and will check site status later");
            retrySleep();
        }
    }

    private boolean isSiteUp(Site site) {
        // Get service beacons for given site - - assume syssvc on all sites share same service name in beacon
        try {
            List<Service> svcs = coordinator.getCoordinatorClient().locateAllServices(site.getUuid(), service.getName(), service.getVersion(),
                    (String) null, null);

            List<String> nodeList = new ArrayList<String>();
            for(Service svc : svcs) {
                nodeList.add(svc.getNodeId());
            }
            log.info("Site {} is up. active nodes {}", site.getUuid(), StringUtils.join(nodeList, ","));
            return true;
        } catch (CoordinatorException ex) {
            if (ex.getServiceCode() == ServiceCode.COORDINATOR_SVC_NOT_FOUND) {
                return false; // no service beacon found for given site
            }
            log.error("Unexpected error when checking site service becons", ex);
            return true;
        }
    }

}
