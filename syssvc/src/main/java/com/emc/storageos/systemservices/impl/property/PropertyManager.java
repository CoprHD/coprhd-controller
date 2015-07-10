/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices.impl.property;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.ConfigVersion;
import com.emc.storageos.coordinator.client.model.PowerOffState;
import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.util.VdcConfigUtil;
import com.emc.storageos.model.property.PropertiesMetadata;
import com.emc.storageos.model.property.PropertyMetadata;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.exceptions.InvalidLockOwnerException;
import com.emc.storageos.systemservices.exceptions.SysClientException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.util.AbstractManager;

public class PropertyManager extends AbstractManager {
    private static final Logger log = LoggerFactory.getLogger(PropertyManager.class);

    private final static int TIME_LIMIT_FOR_INITIATING_POWEROFF = 60000;

    private static final String POWEROFFTOOL_COMMAND = "/etc/powerofftool";
    private static final String VDC_IDS_KEY= "vdc_ids";

    // set to 2.5 minutes since it takes over 2m for ssh to timeout on non-reachable hosts
    private static final long SHUTDOWN_TIMEOUT_MILLIS = 150000;
    private static final int SLEEP_MS = 100;

    private boolean shouldReboot=false;

    // bean properties
    private long powerOffStateChangeTimeout;
    private long powerOffStateProbeInterval;

    private DbClient dbClient;

    // local and target info properties
    private PropertyInfoExt targetPropInfo;
    private PowerOffState targetPowerOffState;
    private PropertyInfoExt localNodePropInfo;
    private PropertyInfoExt localTargetPropInfo;
    private String localConfigVersion;
    private PropertyInfoExt localVdcPropInfo;
    private PropertyInfoExt targetVdcPropInfo;

    private HashSet<String> poweroffAgreementsKeeper = new HashSet<>();

    public HashSet<String> getPoweroffAgreementsKeeper(){
        return poweroffAgreementsKeeper;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setPowerOffStateChangeTimeout(long powerOffStateChangeTimeout) {
        this.powerOffStateChangeTimeout = powerOffStateChangeTimeout;
    }

    public void setPowerOffStateProbeInterval(long powerOffStateProbeInterval) {
        this.powerOffStateProbeInterval = powerOffStateProbeInterval;
    }

    public boolean initiatePoweroff(boolean forceSet) {
        final List<String> svcIds  = coordinator.getAllNodes();
        final String       mySvcId = coordinator.getMySvcId();
        svcIds.remove(mySvcId);
        Set<String> controlerSyssvcIdSet = new HashSet<String>();
        for (String svcId : svcIds){
        	if (svcId.matches("syssvc-\\d")) controlerSyssvcIdSet.add(svcId);
        }

        log.info("Tell other node it's ready to power off");


        for (String svcId : controlerSyssvcIdSet) {
            try {
                SysClientFactory.getSysClient(coordinator.getNodeEndpointForSvcId(svcId))
                        .post(URI.create(SysClientFactory.URI_SEND_POWEROFF_AGREEMENT.getPath() + "?sender=" + mySvcId),null,null);
            } catch (SysClientException e) {
                throw APIException.internalServerErrors.poweroffError(svcId, e);
            }
        }
        long endTime = System.currentTimeMillis() + TIME_LIMIT_FOR_INITIATING_POWEROFF;
        while (true) {
            if (System.currentTimeMillis() > endTime) {
                if(forceSet){
                    return true;
                } else {
                    log.error("Timeout. initiating poweroff failed.");
                    log.info("The received agreements are: " + this.getPoweroffAgreementsKeeper().toString());
                    return false;
                }
            }
            if (poweroffAgreementsKeeper.containsAll(controlerSyssvcIdSet)) {
                return true;
            } else {
                log.debug("Sleep and wait for poweroff agreements for other nodes");
                sleep(SLEEP_MS);
            }
        }
    }

    public void poweroffCluster(){
        log.info("powering off the cluster!");
        final String[] cmd = {POWEROFFTOOL_COMMAND};
        Exec.sudo(SHUTDOWN_TIMEOUT_MILLIS, cmd);
    }

    @Override
    protected URI getWakeUpUrl() {
        return SysClientFactory.URI_WAKEUP_PROPERTY_MANAGER;
    }

    @Override
    protected void innerRun() {
        final String svcId = coordinator.getMySvcId();

        while (doRun) {
            log.debug("Main loop: Start");

            shortSleep = false;

            if (shouldReboot) {
                reboot();
            }

            // Step0: check if we have the property lock
            boolean hasLock;
            try {
                hasLock = coordinator.hasPersistentLock(svcId, propertyLockId);
            } catch (Exception e) {
                log.info("Step1: Failed to verify if the current node has the property lock ", e);
                retrySleep();
                continue;
            }

            if (hasLock) {
                try {
                    coordinator.releasePersistentLock(svcId, propertyLockId);
                    log.info("Step0: Released property lock for node: {}", svcId);
                    wakeupOtherNodes();
                } catch (InvalidLockOwnerException e) {
                    log.error("Step0: Failed to release the property lock: Not owner.");
                } catch (Exception e) {
                    log.info("Step0: Failed to release the property lock and will retry: {}", e.getMessage());
                    retrySleep();
                    continue;
                }
            }

            // Step1: publish current state, and set target if empty
            try {
                initializeLocalAndTargetInfo(svcId);
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

            // Step3: if target property is changed, update
            log.info("Step3: If target property is changed, update");
            if (localTargetPropInfo != null && targetPropInfo != null &&
                    !localConfigVersion.equals(targetPropInfo.getProperty(PropertyInfoExt.CONFIG_VERSION))) {
                log.info("Step3a: Current properties are not same as target properties. Updating.");
                log.debug("Current local target properties: " + localTargetPropInfo);
                log.debug("Target properties: " + targetPropInfo);

                try {
                    updateProperties(svcId);
                } catch (Exception e) {
                    log.info("Step3a: Update failed and will be retried: {}", e.getMessage());
                    // Restart the loop immediately so that we release the upgrade lock.
                    continue;
                }
                continue;
            }

            log.info("Step4: If VDC configuration is changed update");
            if ( vdcPropertiesChanged() ) {
                log.info("Step4: Current vdc properties are not same as target vdc properties. Updating.");
                log.debug("Current local vdc properties: " + localVdcPropInfo);
                log.debug("Target vdc properties: " + targetVdcPropInfo);

                try {
                    updateVdcProperties(svcId);
                } catch (Exception e) {
                    log.info("Step4: VDC properties update failed and will be retried: {}", e.getMessage());
                    // Restart the loop immediately so that we release the upgrade lock.
                    continue;
                }
                continue;
            }

            if (shouldReboot == false) {
                // Step5: sleep
                log.info("Step5: sleep");
                longSleep();
            }
        }
    }

    /**
     *  If target poweroff state is not NONE, that means user has set it to STARTED.
     *  in the checkAllNodesAgreeToPowerOff, all nodes, including control nodes and data nodes
     *  will start to publish their poweroff state in the order of [NOTICED, ACKNOWLEDGED, POWEROFF].
     *  Every node can publish the next state only if it sees the previous state are found on every other nodes.
     *  By doing this, we can gaurantee that all nodes receive the acknowledgement of powering among each other,
     *  we can then safely poweroff.
     *  No matter the poweroff failed or not, at the end, we reset the target poweroff state back to NONE.
     *  CTRL-11690: the new behavior is if an agreement cannot be reached, a best-effort attempt to poweroff the
     *  remaining nodes will be made, as if the force parameter is provided.
     */
    private void gracefulPoweroffCluster() {
        if (targetPowerOffState != null && targetPowerOffState.getPowerOffState() != PowerOffState.State.NONE) {
            boolean forceSet = targetPowerOffState.getPowerOffState() == PowerOffState.State.FORCESTART;
            log.info("Step2: Trying to reach agreement with timeout on cluster poweroff");
            if (checkAllNodesAgreeToPowerOff(forceSet) && initiatePoweroff(forceSet)){
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
     * Get node scope properties
     *   UpgradeManager will publish the node scope properties as node information into coordinator
     *   Node scope properties are invariants.
     *
     * We check to see if a property is in metadata or not.
     *    If it is, it is a target property; If not, it is a local property
     *
     * @param localPropInfo     local property info read from /etc/config.properties
     * @return      node scope properties
     */
    private PropertyInfoExt getNodeScopeProperties(final PropertyInfoExt localPropInfo) {
        Map<String, PropertyMetadata> metadata = PropertiesMetadata.getGlobalMetadata();
        PropertyInfoExt localScopeProps = new PropertyInfoExt();

        for (Entry<String, String> entry : localPropInfo.getAllProperties().entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();

            if (!metadata.containsKey(key)) {
                localScopeProps.addProperty(key, value);
            }
        }

        return localScopeProps;
    }

    /**
     * Get local target property info
     *
     * For control node, properties that can be found in metadata are target properties
     * For extra node, not only exist in metadata, but also ControlNodeOnly==false
     *
     * @param localPropInfo
     * @return
     */
    private PropertyInfoExt getLocalTargetPropInfo(final PropertyInfoExt localPropInfo) {
        Map<String, PropertyMetadata> metadata = PropertiesMetadata.getGlobalMetadata();
        PropertyInfoExt localTargetProps = new PropertyInfoExt();

        for (Entry<String, String> entry : localPropInfo.getAllProperties().entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();

            if (metadata.containsKey(key)) {
                if (coordinator.isControlNode()) {
                    localTargetProps.addProperty(key, value);
                } else {
                    if (!metadata.get(key).getControlNodeOnly()) {
                        localTargetProps.addProperty(key, value);
                    }
                }
            }
        }

        return localTargetProps;
    }

    /**
     * Combine nodeScopePropInfo with targetPropInfo
     *
     * @param targetPropInfo    target property info
     * @param nodeScopePropInfo node scope property info
     * @return      combined property info
     */
    private PropertyInfoExt combineProps(final PropertyInfoExt targetPropInfo, final PropertyInfoExt nodeScopePropInfo) {
        PropertyInfoExt combinedProps = new PropertyInfoExt();

        for (Entry<String, String> entry : targetPropInfo.getAllProperties().entrySet()) {
            combinedProps.addProperty(entry.getKey(), entry.getValue());
        }

        for (Entry<String, String> entry : nodeScopePropInfo.getAllProperties().entrySet()) {
            combinedProps.addProperty(entry.getKey(), entry.getValue());
        }

        return combinedProps;
    }

    /**
     * Initialize local and target info
     *
     * @throws Exception
     */
    private void initializeLocalAndTargetInfo(String svcId) throws Exception {
        // publish config_version which is also a target property
        //  used as a flag denoting whether target properties have been changed
        PropertyInfoExt localPropInfo = localRepository.getOverrideProperties();
        localConfigVersion = localPropInfo.getProperty(PropertyInfoExt.CONFIG_VERSION);
        if (localConfigVersion == null) {
            localConfigVersion = "0";
            localPropInfo.addProperty(PropertyInfoExt.CONFIG_VERSION, localConfigVersion);
        }
        coordinator.setNodeSessionScopeInfo(new ConfigVersion(localConfigVersion));
        log.info("Step1a: Local config version: {}", localConfigVersion);
        // set node scope properties for the 1st time only since they are invariants.
        localNodePropInfo = coordinator.getNodeGlobalScopeInfo(PropertyInfoExt.class, "propertyinfo", svcId);
        // The PropertyInfoExt object will be persisted in the zookeeper path config/propertyinfo/(svcId)
        if (localNodePropInfo == null) {
            localNodePropInfo = getNodeScopeProperties(localPropInfo);
            coordinator.setNodeGlobalScopeInfo(localNodePropInfo, "propertyinfo", svcId);
         // The PropertyInfoExt object can be fetched from the zookeeper path config/propertyinfo/(svcId)
            log.info("Step1a: Local node scope properties: {}", localNodePropInfo);
        }
        // get local target property info
        localTargetPropInfo = getLocalTargetPropInfo(localPropInfo);
        log.debug("Step1a: Local target properties: {}", localTargetPropInfo);

        // set target if empty
        targetPropInfo = coordinator.getTargetInfo(PropertyInfoExt.class);
        targetPowerOffState = coordinator.getTargetInfo(PowerOffState.class);
        if (targetPropInfo == null || targetPowerOffState == null) {
            // only control node can set target
            try {
                // Set the updated propperty info in coordinator
                coordinator.setTargetInfo(localPropInfo);
                coordinator.setTargetInfo(new PowerOffState(PowerOffState.State.NONE));

                targetPropInfo = coordinator.getTargetInfo(PropertyInfoExt.class);
                log.info("Step1b: Target property set to local state: {}", targetPropInfo);
                targetPowerOffState = coordinator.getTargetInfo(PowerOffState.class);
                log.info("Step1b: Target poweroff state set to: {}", PowerOffState.State.NONE);
            } catch (CoordinatorClientException e) {
                log.info("Step1b: Wait another control node to set target");
                retrySleep();
                throw e;
            }
        }

        // Initialize vdc prop info
        localVdcPropInfo = localRepository.getVdcPropertyInfo();
        targetVdcPropInfo = loadVdcConfigFromDatabase();
        if ( localVdcPropInfo.getProperty(VdcConfigUtil.VDC_CONFIG_HASHCODE)  == null ) {
            localRepository.setVdcPropertyInfo(targetVdcPropInfo);
            localVdcPropInfo = localRepository.getVdcPropertyInfo();
            String vdc_ids= targetVdcPropInfo.getProperty(VDC_IDS_KEY);
            String[] vdcIds = vdc_ids.split(",");
            if ( vdcIds.length > 1) {
                log.info("More than one Vdc, so set reboot flag");
                shouldReboot=true;
            }
        }
    }

    /**
     * Update properties
     *
     * @param svcId            node service id
     * @throws Exception
     */
    private void updateProperties(String svcId) throws Exception {
        if (targetPropInfo.TARGET_PROPERTY.equals(targetPropInfo.OLD_TARGET_PROPERTY)) {
        	coordinator.removeTargetInfo(targetPropInfo, true);
        }
        PropertyInfoExt diffProperties = new PropertyInfoExt(targetPropInfo.getDiffProperties(localTargetPropInfo));
        PropertyInfoExt override_properties = new PropertyInfoExt(localRepository.getOverrideProperties().getAllProperties());
        log.info("Step3a: Updating User Changed properties file: {}", override_properties);
        PropertyInfoExt updatedUserChangedProps = combineProps(override_properties, diffProperties);
        if (diffProperties.hasRebootProperty()) {
            if (! getPropertyLock(svcId)) {
                retrySleep();
            } else if (! isQuorumMaintained()) {
                try {
                    coordinator.releasePersistentLock(svcId, propertyLockId);
                } catch (Exception e) {
                    log.error("Failed to release the property lock:", e);
                }
                retrySleep();
            } else {
                log.info("Step3a: Reboot property found.");
                localRepository.setOverrideProperties(updatedUserChangedProps);
                log.info("Step3a: Updating properties: {}", updatedUserChangedProps);
                reboot();
            }
        } else if (diffProperties.hasReconfigProperty() || ! diffProperties.getNotifierTags().isEmpty()) {
            log.info("Step3a: Reconfig property found or notifiers specified.");

            // CTRL-9860: don't update the local config version until everything is done.
            String targetVersion = targetPropInfo.getProperty(PropertyInfoExt.CONFIG_VERSION);
            updatedUserChangedProps.addProperty(PropertyInfoExt.CONFIG_VERSION, localConfigVersion);
            localRepository.setOverrideProperties(updatedUserChangedProps);
            log.info("Step3a: Updating properties without updating the config version: {}", updatedUserChangedProps);
            if (diffProperties.hasReconfigAttributeWithoutNotifiers()) {
                // this is the old-school "complete" reconfig that takes no notifiers as arguments.
                // moving forward this will diminish
                // i.e., all reconfigRequired properties will have notifier specified.
                localRepository.reconfig();
            } else if (diffProperties.hasReconfigProperty()) {
                reconfigProperties(diffProperties);
            }

            // the notifier list can be empty, in which case nothing will be done.
            notifyPropertyChanges(diffProperties);

            // update the local config version to target version now
            log.info("Step3a: Updating the config version to {}", targetVersion);
            updatedUserChangedProps.addProperty(PropertyInfoExt.CONFIG_VERSION, targetVersion);
            localRepository.setOverrideProperties(updatedUserChangedProps);

        } else {
            log.info("Step3a: No reboot property found.");
            localRepository.setOverrideProperties(updatedUserChangedProps);
            log.info("Step3a: Updating properties: {}", updatedUserChangedProps);
        }
    }

    private void reconfigProperties(PropertyInfoExt diffProperties) {
        // only get the notifiers that requires reconfig as well
        List<String> notifierTagList = diffProperties.getNotifierTags(true);
        String notifierTags = StringUtils.join(notifierTagList, " ");
        log.info("Step3a: Reconfiguring properties related to {}", notifierTags);

        try {
            localRepository.reconfigProperties(notifierTags);
        } catch (Exception e) {
            log.error("Step3a: Fail to reconfig properties related to {}", notifierTags, e);
        }
    }

    private void notifyPropertyChanges(PropertyInfoExt diffProperties) {
        List<String> notifierTags = diffProperties.getNotifierTags();
        for (String notifierTag : notifierTags) {
            log.info("Step3a: Calling notifier {}", notifierTag);
            try {
                Notifier notifier = Notifier.getInstance(notifierTag);
                if (notifier != null)
                    notifier.doNotify();
            } catch (Exception e) {
                log.error("Step3a: Fail to invoke notifier {}", notifierTag, e);
            }
        }
    }

    /**
     * Load the vdc vonfiguration from the database
     * @return
     */
    private PropertyInfoExt loadVdcConfigFromDatabase() {
        VdcConfigUtil vdcConfigUtil = new VdcConfigUtil();
        vdcConfigUtil.setDbclient(dbClient);
        return new PropertyInfoExt((Map)vdcConfigUtil.genVdcProperties());
    }

    /**
     * Check if VDC configuration is different in the database vs. what is stored locally
     * @return
     */
    private boolean vdcPropertiesChanged() {
        if (!coordinator.isControlNode()) {
            return false;
        }

        int localVdcConfigHashcode  = localVdcPropInfo.getProperty(VdcConfigUtil.VDC_CONFIG_HASHCODE)  == null ? 0 : Integer.parseInt(localVdcPropInfo.getProperty(VdcConfigUtil.VDC_CONFIG_HASHCODE));
        int targetVdcConfigHashcode = targetVdcPropInfo.getProperty(VdcConfigUtil.VDC_CONFIG_HASHCODE) == null ? 0 : Integer.parseInt(targetVdcPropInfo.getProperty(VdcConfigUtil.VDC_CONFIG_HASHCODE));

        return localVdcConfigHashcode != targetVdcConfigHashcode;
    }

    /**
     * Update vdc properties and reboot the node if
     *
     * @param svcId            node service id
     * @throws Exception
     */
    private void updateVdcProperties(String svcId) throws Exception {
        // If the change is being done to create a multi VDC configuration or to reduce to a
        // multi VDC configuration a reboot is needed.  If only operating on a single VDC
        // do not reboot the nodes.
        if( targetVdcPropInfo.getProperty(VdcConfigUtil.VDC_IDS).contains(",")
            || localVdcPropInfo.getProperty(VdcConfigUtil.VDC_IDS).contains(",")) {
            log.info("Step4: Acquiring property lock for vdc properties change.");
            if (! getPropertyLock(svcId)) {
                retrySleep();
            } else if (! isQuorumMaintained()) {
                try {
                    coordinator.releasePersistentLock(svcId, propertyLockId);
                } catch (Exception e) {
                    log.error("Failed to release the property lock:", e);
                }
                retrySleep();
            } else {
                log.info("Step4: Setting vdc properties and rebooting for multi-vdc config change");
                localRepository.setVdcPropertyInfo(targetVdcPropInfo);
                reboot();
            }
        } else {
            log.info("Step4: Setting vdc properties not rebooting for single VDC change");
            localRepository.setVdcPropertyInfo(targetVdcPropInfo);
        }
    }

    /**
     * Try to acquire the property lock, like upgrade lock, this also requires rolling reboot
     * so upgrade lock should be acquired at the same time
     * @param svcId
     * @return
     */
    private boolean getPropertyLock(String svcId) {
        if (!coordinator.getPersistentLock(svcId, propertyLockId)) {
            log.info("Acquiring property lock failed. Retrying...");
            return false;
        }

        if (!coordinator.getPersistentLock(svcId, upgradeLockId)) {
            log.info("Acquiring upgrade lock failed. Retrying...");
            return false;
        }

        //release the upgrade lock
        try {
            coordinator.releasePersistentLock(svcId, upgradeLockId);
        } catch (Exception e) {
            log.error("Failed to release the upgrade lock:", e);
        }
        log.info("Successfully acquired the property lock.");
        return true;
    }

    /**
     * Check all nodes agree to power off
     *  Work flow:
     *    Each node publishes NOTICED, then wait to see if all other nodes got the NOTICED.
     *    If true, continue to publish ACKNOWLEDGED; if false, return false immediately. Poweroff will fail.
     *    Same for ACKNOWLEDGED.
     *    After a node see others have the ACKNOWLEDGED published, it can power off.
     *
     *    If we let the node which first succeeded to see all ACKNOWLEDGED to power off first,
     *    other nodes may fail to see the ACKNOWLEDGED signal since the 1st node is shutting down.
     *    So we defined an extra STATE.POWEROFF state, which won't check the count of control nodes.
     *    Nodes in POWEROFF state are free to poweroff.
     * @param forceSet
     * @return  true if all node agree to poweroff; false otherwise
     */
    private boolean checkAllNodesAgreeToPowerOff(boolean forceSet) {
        while (true) {
            try {
                // Send NOTICED signal and verify
                publishNodePowerOffState(PowerOffState.State.NOTICED);
                poweroffAgreementsKeeper = new HashSet<>();
                if (!waitClusterPowerOffStateNotLessThan(PowerOffState.State.NOTICED, !forceSet)) {
                    log.error("Failed to get {} signal from all other nodes", PowerOffState.State.NOTICED);
                    return false;
                }
                // Send ACKNOWLEDGED signal and verify
                publishNodePowerOffState(PowerOffState.State.ACKNOWLEDGED);
                if (!waitClusterPowerOffStateNotLessThan(PowerOffState.State.ACKNOWLEDGED, !forceSet)) {
                    log.error("Failed to get {} signal from all other nodes", PowerOffState.State.ACKNOWLEDGED);
                    return false;
                }

                // Send POWEROFF signal and verify
                publishNodePowerOffState(PowerOffState.State.POWEROFF);
                if (!waitClusterPowerOffStateNotLessThan(PowerOffState.State.POWEROFF, !forceSet)) {
                    log.error("Failed to get {} signal from all other nodes", PowerOffState.State.POWEROFF);
                    return false;
                }

                return true;
            } catch (Exception e) {
                log.error("Step2: checkAllNodesAgreeToPowerOff failed: {} ", e);
            }
        }
    }

    /**
     * Reset target power off state back to NONE
     */
    private void resetTargetPowerOffState() {
        poweroffAgreementsKeeper = new HashSet<String>();
        while (true) {
            try {
                if (coordinator.isControlNode()) {
                    try {
                        coordinator.setTargetInfo(new PowerOffState(PowerOffState.State.NONE), false);
                        log.info("Step2: Target poweroff state change to: {}", PowerOffState.State.NONE);
                    } catch (CoordinatorClientException e) {
                        log.info("Step2: Wait another control node to set target poweroff state");
                        retrySleep();
                    }
                } else {
                    log.info("Step2: Wait control node to set target poweroff state");
                    retrySleep();
                }

                // exit only when target poweroff state is NONE
                if (coordinator.getTargetInfo(PowerOffState.class).getPowerOffState() == PowerOffState.State.NONE) {
                    break;
                }
            } catch (Exception e) {
                retrySleep();
                log.info("Step2: reset cluster poweroff state retrying. {}", e);
            }
        }
    }

    /**
     * Publish node power off state
     * @param toState
     * @throws com.emc.storageos.systemservices.exceptions.CoordinatorClientException
     */
    private void publishNodePowerOffState(PowerOffState.State toState) throws CoordinatorClientException {
        log.info("Step2: Send {} signal", toState);
        coordinator.setNodeSessionScopeInfo(new PowerOffState(toState));
    }

    /**
     * Wait cluster power off state change to a state not less than specified state
     * @param state
     * @param checkNumOfControlNodes
     * @return  true if all nodes' poweroff state are equal to specified state
     */
    private boolean waitClusterPowerOffStateNotLessThan(PowerOffState.State state, boolean checkNumOfControlNodes) {
        long expireTime = System.currentTimeMillis() + powerOffStateChangeTimeout;
        while (true) {
            if (coordinator.verifyNodesPowerOffStateNotBefore(state, checkNumOfControlNodes)) {
                return true;
            }

            sleep(powerOffStateProbeInterval);
            if (System.currentTimeMillis() >= expireTime) {
                return false;
            }
        }
    }
}
