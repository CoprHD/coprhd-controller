/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.installer.controller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import charva.awt.Component;
import charva.awt.event.ItemEvent;
import charva.awt.event.ItemListener;
import charvax.swing.JRadioButton;

import com.emc.storageos.installer.util.InstallerConstants;
import com.emc.storageos.services.util.Configuration;
import com.emc.storageos.installer.widget.SelectButtonPanel;

public class RejoinClusterController implements IConfigPanelController {

    private static final Logger log = LoggerFactory.getLogger(RejoinClusterController.class);
    private Configuration config;
    private SelectButtonPanel panel;
    private Set<Configuration> availabelClusters;
    public String selectedCluster;
    private Configuration selectedConfig;

    public RejoinClusterController(Configuration config, SelectButtonPanel panel,
            Set<Configuration> availabelClusters) {
        this.config = config;
        this.panel = panel;
        this.availabelClusters = availabelClusters;
        setupEventListener();
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
                selectedCluster = ((JRadioButton) source).getText();
                log.info("Selected cluster: {}", selectedCluster);
                if (selectedCluster.contains("empty")) {
                    panel.getRoot().displayErrorMessage(new String[] {
                            "Could not find an available cluster to join.",
                            "Please exit installer, fix the error and re-try." });
                }
            }
        }
    }

    /*
     * Save contents from selected cluster to Configuration object
     */
    private void saveClusterConfig() {
        // get all the alive nodes from clusters, so it can be updated to configuration
        // and used by node selection page to show which nodes currently available
        Set<String> aliveNodes = new HashSet<String>();
        for (Configuration cluster : availabelClusters) {
            String selectedClusterVip = selectedCluster.split(InstallerConstants.CLUSTER_LABEL_DELIMITER)[0];
            if (selectedClusterVip.equals(cluster.getNetworkVip())) {
                selectedConfig = cluster;
                aliveNodes.addAll(cluster.getAliveNodes());
            }
        }

        if (selectedConfig != null) {
            // save properties from selected cluster except node id, disk and net i/f
            config.setNodeCount(selectedConfig.getNodeCount());
            // update the list with consolidated alive nodes from all cluster
            config.getAliveNodes().clear();
            config.setAliveNodes(new ArrayList<String>(aliveNodes));
            config.getNetworkIpv4Config().putAll(selectedConfig.getNetworkIpv4Config());
            config.getNetworkIpv6Config().putAll(selectedConfig.getNetworkIpv6Config());
        }
    }

    @Override
    public String[] configurationIsCompleted() {
        String err = null;
        saveClusterConfig();
        log.info("Saving cluster info: {}", config.toString());
        return (err == null) ? null : new String[] { err };
    }

}
