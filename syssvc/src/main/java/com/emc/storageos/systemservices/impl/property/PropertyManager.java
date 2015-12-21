/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.property;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.ConfigVersion;
import com.emc.storageos.coordinator.client.model.PowerOffState;
import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.model.property.PropertiesMetadata;
import com.emc.storageos.model.property.PropertyMetadata;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.exceptions.InvalidLockOwnerException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.util.AbstractManager;

public class PropertyManager extends AbstractManager {
    private static final Logger log = LoggerFactory.getLogger(PropertyManager.class);

    private boolean shouldReboot = false;

    // local and target info properties
    private PropertyInfoExt targetPropInfo;
    private PropertyInfoExt localNodePropInfo;
    private PropertyInfoExt localTargetPropInfo;
    private String localConfigVersion;


    @Override
    protected URI getWakeUpUrl() {
        return SysClientFactory.URI_WAKEUP_PROPERTY_MANAGER;
    }

    /**
     * Register repository info listener to monitor repository version changes
     */
    private void addPropertyInfoListener() {
        try {
            coordinator.getCoordinatorClient().addNodeListener(new PropertyInfoListener());
        } catch (Exception e) {
            log.error("Fail to add node listener for property info target znode", e);
            throw APIException.internalServerErrors.addListenerFailed();
        }
        log.info("Successfully added node listener for property info target znode");
    }

    /**
     * the listener class to listen the property target node change.
     */
    class PropertyInfoListener implements NodeListener{
        public String getPath() {
            return String.format("/config/%s/%s", PropertyInfoExt.TARGET_PROPERTY, PropertyInfoExt.TARGET_PROPERTY_ID);
        }

        /**
         * called when user update the target version
         */
        @Override
        public void nodeChanged() {
            log.info("Property info changed. Waking up the property manager...");
            wakeup();
        }

        /**
         * called when connection state changed.
         */
        @Override
        public void connectionStateChanged(State state) {
            log.info("Property info connection state changed to {}", state);
            if (state.equals(State.CONNECTED)) {
                log.info("Curator (re)connected. Waking up the property manager...");
                wakeup();
            }
        }
    }

    @Override
    protected void innerRun() {
        // need to distinguish persistent locks acquired from UpgradeManager/VdcManager/PropertyManager
        // otherwise they might release locks acquired by others when they start
        final String svcId = String.format("%s,property", coordinator.getMySvcId());

        addPropertyInfoListener();

        while (doRun) {
            log.debug("Main loop: Start");

            shortSleep = false;

            if (shouldReboot) {
                reboot();
            }

            // Step0: check if we have the property lock
            boolean hasLock;
            try {
                hasLock = hasRebootLock(svcId);
            } catch (Exception e) {
                log.info("Step1: Failed to verify if the current node has the property lock ", e);
                retrySleep();
                continue;
            }

            if (hasLock) {
                try {
                    releaseRebootLock(svcId);
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

            if (shouldReboot == false) {
                // Step5: sleep
                log.info("Step5: sleep");
                longSleep();
            }
        }
    }

    /**
     * Get node scope properties
     * UpgradeManager will publish the node scope properties as node information into coordinator
     * Node scope properties are invariants.
     * 
     * We check to see if a property is in metadata or not.
     * If it is, it is a target property; If not, it is a local property
     * 
     * @param localPropInfo local property info read from /etc/config.properties
     * @return node scope properties
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
     * @param targetPropInfo target property info
     * @param nodeScopePropInfo node scope property info
     * @return combined property info
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
        // used as a flag denoting whether target properties have been changed
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
        targetPropInfo = coordinator.getTargetProperties();
        if (targetPropInfo == null) {
            // only control node can set target
            try {
                // Set the updated propperty info in coordinator
                coordinator.setTargetProperties(localPropInfo.getAllProperties());
                coordinator.setTargetInfo(new PowerOffState(PowerOffState.State.NONE));

                targetPropInfo = coordinator.getTargetInfo(PropertyInfoExt.class);
                log.info("Step1b: Target property set to local state: {}", targetPropInfo);
            } catch (CoordinatorClientException e) {
                log.info("Step1b: Wait another control node to set target");
                retrySleep();
                throw e;
            }
        }
    }

    /**
     * Update properties
     * 
     * @param svcId node service id
     * @throws Exception
     */
    private void updateProperties(String svcId) throws Exception {
        PropertyInfoExt diffProperties = new PropertyInfoExt(targetPropInfo.getDiffProperties(localTargetPropInfo));
        PropertyInfoExt override_properties = new PropertyInfoExt(localRepository.getOverrideProperties().getAllProperties());
        log.info("Step3a: Updating User Changed properties file: {}", override_properties);
        PropertyInfoExt updatedUserChangedProps = combineProps(override_properties, diffProperties);
        if (diffProperties.hasRebootProperty()) {
            if (!getRebootLock(svcId)) {
                retrySleep();
            } else if (!isQuorumMaintained()) {
                releaseRebootLock(svcId);
                retrySleep();
            } else {
                log.info("Step3a: Reboot property found.");
                localRepository.setOverrideProperties(updatedUserChangedProps);
                log.info("Step3a: Updating properties: {}", updatedUserChangedProps);
                reboot();
            }
        } else if (diffProperties.hasReconfigProperty() || !diffProperties.getNotifierTags().isEmpty()) {
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
                if (notifier != null) {
                    notifier.doNotify();
                }
            } catch (Exception e) {
                log.error("Step3a: Fail to invoke notifier {}", notifierTag, e);
            }
        }
    }
}
