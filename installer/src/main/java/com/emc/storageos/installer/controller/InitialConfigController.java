/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.installer.controller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.emc.storageos.model.property.PropertyConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import charva.awt.Component;
import charva.awt.event.ItemEvent;
import charva.awt.event.ItemListener;
import charvax.swing.JRadioButton;

import com.emc.storageos.installer.util.InstallerConstants;
import com.emc.storageos.installer.util.InstallerOperation;
import com.emc.storageos.services.util.Configuration;
import com.emc.storageos.services.util.ServerProbe;
import com.emc.storageos.installer.widget.SelectButtonPanel;

/**
 * Class implements the control on the Cluster screen in config mode for ECS.
 * 
 */
public class InitialConfigController implements IConfigPanelController {
    private static final Logger log = LoggerFactory.getLogger(InitialConfigController.class);
    private Configuration config;
    private SelectButtonPanel panel;
    private String selectedClusterName;
    private Set<Configuration> availabelClusters;
    private boolean isNewConfig = true;
    private Configuration selectedConfig;
    private String[] status = null;

    public InitialConfigController(Configuration config, SelectButtonPanel panel,
            Set<Configuration> availabelClusters) {
        this.config = config;
        this.panel = panel;
        this.availabelClusters = availabelClusters;
        setupEventListener();
    }

    public boolean isNewConfig() {
        return isNewConfig;
    }

    private void setupEventListener() {
        panel.selectList1ItemListener(new SelectClusterListener());
    }

    /**
     * Class listens on cluster selection
     */
    class SelectClusterListener implements ItemListener {
        @Override
        public void itemStateChanged(ItemEvent ie) {
            int statechange = ie.getStateChange();
            Component source = (Component) ie.getSource();
            if (statechange == ItemEvent.SELECTED) {
                selectedClusterName = ((JRadioButton) source).getText();
                log.info("Selected cluster: {}", selectedClusterName);
                clearConfiguration();
                if (selectedClusterName.equals(InstallerConstants.CLUSTER_CONFIG_NEW_CONFIG_LABEL)) {
                    isNewConfig = true;
                    if (!meetMinimumHwRequirement()) {
                        log.warn("Node does not meet minimum H/W requirement");
                    }
                } else {
                    isNewConfig = false;
                    getSelectedClusterConfiguration();
                    if (!hasSameHardware()) {
                        log.warn("Node does not have the same H/W requirement");
                    }
                }
            }
        }

        private boolean meetMinimumHwRequirement() {
            log.debug("Check if local server meets minimum hardware requirement for installation.");
            if (!ServerProbe.getInstance().isMetMinimumRequirement()) {
                status = new String[] { InstallerConstants.ERROR_MSG_NOT_MEET_MIN_REQ_1,
                        InstallerConstants.ERROR_MSG_NOT_MEET_MIN_REQ_2,
                        InstallerConstants.ERROR_MSG_NOT_MEET_MIN_REQ_3,
                        InstallerConstants.ERROR_MSG_NOT_MEET_MIN_REQ_4 };
                return false;
            } else {
                status = null;
                return true;
            }
        }

        private boolean hasSameHardware() {
            log.debug("Check if local hardware are the same as selected cluster {}",
                    selectedClusterName);
            if (selectedConfig != null && selectedConfig.getHwConfig() != null
                    && !selectedConfig.getHwConfig().isEmpty()) {
                // TODO: reassess later
                // for now in config mode, don't compare disk
                String compareMsg = InstallerOperation.compareHardware(selectedConfig.getHwConfig(), true);
                if (compareMsg != null) {
                    status = new String[] { compareMsg,
                            InstallerConstants.ERROR_MSG_HW_NOT_SAME_2 };
                    return false;
                } else {
                    status = null;
                }
            }
            return true;
        }

        private void getSelectedClusterConfiguration() {
            // get all the alive nodes from clusters, so it can be updated to configuration
            // and used by node selection page to show which nodes currently available
            Set<String> aliveNodes = new HashSet<String>();
            for (Configuration cluster : availabelClusters) {
                String selectedClusterVip = selectedClusterName.split(InstallerConstants.CLUSTER_LABEL_DELIMITER)[0];
                if (selectedClusterVip.equals(cluster.getNetworkVip())) {
                    selectedConfig = cluster;
                    aliveNodes.add(cluster.getNodeId());
                }
            }
            selectedConfig.setAliveNodes(new ArrayList<String>(aliveNodes));
            log.info("Selected cluster config: {}", selectedConfig.toString());
        }

        /*
         * Clears the configuration contents
         */
        private void clearConfiguration() {
            config.setNodeCount(0);
            config.setNodeId(null);
            config.getAliveNodes().clear();
            config.getHwConfig().remove(PropertyConstants.PROPERTY_KEY_DISK);
            config.getNetworkIpv4Config().clear();
            config.getNetworkIpv6Config().clear();
        }
    }

    /*
     * Save contents from selected cluster to Configuration object
     */
    private void saveClusterConfig() {
        log.debug("Before saving config {}", config.toString());
        if (selectedConfig != null) {
            log.info("Selected cluster {} configuration {}", selectedClusterName,
                    selectedConfig.toString());

            config.setNodeCount(selectedConfig.getNodeCount());
            config.setAliveNodes(selectedConfig.getAliveNodes());
            config.getHwConfig().putAll(selectedConfig.getHwConfig());

            // clear network setting before putting new ones (node count may
            // changed)
            config.getNetworkIpv4Config().clear();
            config.getNetworkIpv6Config().clear();
            config.getNetworkIpv4Config().putAll(selectedConfig.getNetworkIpv4Config());
            config.getNetworkIpv6Config().putAll(selectedConfig.getNetworkIpv6Config());
            log.info("Saved config {}", config.toString());
        }
    }

    @Override
    public String[] configurationIsCompleted() {
        if (status != null && status.length != 0) {
            return status;
        } else {
            if (!isNewConfig) {
                log.info("Saving selected configuration {}", selectedClusterName);
                saveClusterConfig();
            }
            return null;
        }
    }

}
