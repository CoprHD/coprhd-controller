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
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import charva.awt.Component;
import charva.awt.event.ItemEvent;
import charva.awt.event.ItemListener;
import charvax.swing.JRadioButton;

import com.emc.storageos.services.util.Configuration;
import com.emc.storageos.installer.util.ClusterType;
import com.emc.storageos.installer.util.InstallerConstants;
import com.emc.storageos.installer.util.InstallerOperation;
import com.emc.storageos.services.util.ServerProbe;
import com.emc.storageos.installer.widget.SelectButtonPanel;

/**
 * Class implements the control on the Cluster screen in config mode.
 *
 */
public class ConfigClusterController implements IConfigPanelController {
	private static final Logger log = LoggerFactory.getLogger(ConfigClusterController.class);
	private Configuration config;
	private SelectButtonPanel panel;
	private String selectedCluster;
	private Set<Configuration> availabelClusters;
	private boolean isNewConfig = false;
	private Configuration selectedConfig;
	private boolean isSameClusterType = false;
	private String nodeId;
	private Configuration localConfig;
	private String[] status = null;

	public ConfigClusterController(Configuration config, SelectButtonPanel panel,
			Set<Configuration> availabelClusters, Configuration localConfig) {
		this.config = config;
		this.panel = panel;
		this.availabelClusters = availabelClusters;
		this.localConfig = localConfig;
		setupEventListener();
	}
	
	public boolean isNewConfig() {
		return isNewConfig;
	}
	
	private boolean isReConfig() {
		if (localConfig == null) {
			log.debug("This is ECS case, local config is null");
			return false;
		} else {
			log.debug("This is re-config, local config is NOT null");
			return true;
		}
	}
	
	// get user selected configuration
	public Configuration getSelectedConfig() {
		return selectedConfig;
	}

	private void setupEventListener() {
		panel.selectList1ItemListener(new SelectClusterListener());
		// in the ECS case, user needs to select node id
		if (!isReConfig()) {
			panel.selectList2ItemListener(new SelectNodeIdListener());
		}
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
				JRadioButton button = (JRadioButton) source;
				selectedCluster = button.getText();
				log.info("Selected cluster: {}", selectedCluster);
				clearConfiguration();
				if (selectedCluster.equals(InstallerConstants.CLUSTER_CONFIG_NEW_CONFIG_LABEL)) {
					isNewConfig = true;
					disableNodeIdPanel();
					if (!meetMinimumHwRequirement()) {
						log.warn("Node does not meet minimum H/W requirement for Config");
					}
				} else {
					// check if selected node count is the same as local one if has local config
					if (!isReConfig() || isNodeCountSame(selectedCluster)) {
						isNewConfig = false;
						getConfiguration(selectedCluster);
						isSameClusterType = true;
						if (!hasSameHardware()) {
							log.info("Node does not have the same H/W requirement as selected cluster {}",
									selectedCluster);
						}
					} else {
						isSameClusterType = false;
						selectedConfig = null;
						selectedCluster = null;
					}
				}
			}
		}
		
		private boolean meetMinimumHwRequirement() {
			log.debug("Check if local server meets minimum hardware requirement for config.");
			if (!ServerProbe.getInstance().isMetMinimumRequirement()) {
				status = new String[] {InstallerConstants.ERROR_MSG_NOT_MEET_MIN_REQ_1, 
						InstallerConstants.ERROR_MSG_NOT_MEET_MIN_REQ_2,
						InstallerConstants.ERROR_MSG_NOT_MEET_MIN_REQ_3,
						InstallerConstants.ERROR_MSG_NOT_MEET_MIN_REQ_4};
				return false;
			} else {
                status = null;
                return true;
            }
		}

		private boolean hasSameHardware() {
			log.debug("Check if local hardware are the same as selected cluster {}",
					selectedCluster);
			if (selectedConfig != null && selectedConfig.getHwConfig() != null 
					&& !selectedConfig.getHwConfig().isEmpty()) {
				// TODO: reassess later 
				// for now in config mode, don't compare disk 
				String compareMsg = InstallerOperation.compareHardware(selectedConfig.getHwConfig(), true);
				if (compareMsg != null) {
					status = new String[] {compareMsg, InstallerConstants.ERROR_MSG_HW_NOT_SAME_2};
					return false;
				} else {
                    status = null;
                }
			}
                        return true;
		}
		
		/*
		 * Check node count between selected cluster and local configuration.
		 * @param cluster the selected cluster
		 * @return true if same, false if not same
		 */
		private boolean isNodeCountSame(String cluster) {
			int nodeCount = 0;
			if (cluster.contains(ClusterType.NODE_COUNT_1.getLabel())) {
				nodeCount = ClusterType.NODE_COUNT_1.getCount();
			} else if (cluster.contains(ClusterType.NODE_COUNT_3.getLabel())) {
				nodeCount = ClusterType.NODE_COUNT_3.getCount();
			} else if (cluster.contains(ClusterType.NODE_COUNT_5.getLabel())) {
				nodeCount = ClusterType.NODE_COUNT_5.getCount();
			}
			if (nodeCount == localConfig.getNodeCount()) {
				return true;
			} else {
				log.info("Selected node count: {}, local node count: {}", 
						nodeCount, config.getNodeCount());
				return false;
			}
		}

		/*
		 * Get the Configuration of selected cluster. Set the node id list based on 
		 * the nodes already been taken.
		 * @param selectedCluster
		 *        selected cluster label from GUI (e.g. "10.247.101.174 [2+1 (3 servers)]")
		 */
		private void getConfiguration(String selectedCluster) {
			List<String> nodes = new ArrayList<String>();
			for (Configuration c : availabelClusters) {
                String selectedClusterVip = selectedCluster.split(InstallerConstants.CLUSTER_LABEL_DELIMITER)[0];
				if (selectedClusterVip.equals(c.getNetworkVip())) {
					nodes.add(c.getNodeId());
					selectedConfig = c;
				}
			}
			if (!isReConfig()) {
				resetVisibility();
				resetEnableDisable(nodes);
			}
		}
		
		/*
		 * Clears the configuration contents
		 */
		private void clearConfiguration() {
			status = null;
			nodeId = null;
			config.setNodeCount(0);
			config.setNodeId(null);
			config.getNetworkIpv4Config().clear();
			config.getNetworkIpv6Config().clear();
		}
		
		/*
		 * Reset the visibility of the node id based on the node count of selected cluster.
		 */
		private void resetVisibility() {
			// reset all buttons to invisible and clear the selection made
			// before first
			for (JRadioButton node : panel.getButtonList2()) {
				node.setSelected(false);
				node.setVisible(false);
			}
			// set the label back visible
			panel.getEastLabel().setVisible(true);
			// enable buttons based on node count
			for (int i = 0; i < selectedConfig.getNodeCount(); i++) {
				panel.getButtonList2().get(i).setVisible(true);
			}
		}

		/*
		 * Disable/gray out the node id already taken
		 */
		private void resetEnableDisable(List<String> nodeIdTaken) {
			// get the node already taken and disable it
			for (int i = 0; i < selectedConfig.getNodeCount(); i++) {
				JRadioButton node = panel.getButtonList2().get(i);
				if (nodeIdTaken.contains(node.getText())) {
					// this node already taken, disable it
					node.setEnabled(false);
				} else {
					node.setEnabled(true);
				}
			}
		}
		
		/*
		 * Set the node id list to invisible when new configuration selects
		 */
		private void disableNodeIdPanel() {
			for (JRadioButton node : panel.getButtonList2()) {
				node.setVisible(false);
			}
			panel.getEastLabel().setVisible(false);
		}
	}
	
	/**
	 * Class listens on node id selection
	 */
	class SelectNodeIdListener implements ItemListener {
		@Override
		public void itemStateChanged(ItemEvent ie) {
			int statechange = ie.getStateChange();
			Component source = (Component) ie.getSource();
			if (statechange == ItemEvent.SELECTED) {
				JRadioButton button = (JRadioButton) source;
				nodeId = button.getText();
			}
		}
	}
	
	/*
	 * Save contents from selected cluster to Configuration object
	 */
	private String saveClusterConfig() {
		String err = null;
		log.debug("Before saving config {}", config.toString());
		if (selectedConfig != null) {
			log.debug("Selected cluster {} configuration {}", selectedCluster,
					selectedConfig.toString());

			// save node id from selection in ECS case; in re-config case, save it from local config
			if (!isReConfig()) {
				if (nodeId == null) {
					err = InstallerConstants.CLUSTER_CONFIG_WARN_MSG;
					return err;
				}
				log.debug("This is ECS case, save node id '{}' from selection.", nodeId);
				config.setNodeId(nodeId);
			} else {
				log.debug("This is re-config case, save id '{}' from local config.", localConfig.getNodeId());
				config.setNodeId(localConfig.getNodeId());
			}
			config.setNodeCount(selectedConfig.getNodeCount());
            config.getHwConfig().putAll(selectedConfig.getHwConfig());

			// clear network setting before putting new ones (node count may
			// changed)
			config.getNetworkIpv4Config().clear();
			config.getNetworkIpv6Config().clear();
			config.getNetworkIpv4Config().putAll(
					selectedConfig.getNetworkIpv4Config());
			config.getNetworkIpv6Config().putAll(
					selectedConfig.getNetworkIpv6Config());
			log.info("Saved config {}", config.toString());
		}
		return err;
	}
		
	@Override
	public String[] configurationIsCompleted() {
		if (status != null && status.length != 0) {
			return status;
		} else {
			String err = null;
			if (!isNewConfig) {
				if (isSameClusterType) {
					log.info("Saving selected configuration");
					err = saveClusterConfig();
				} else {
					err = "Selected node count is not the same as local one.";
				}
			}
			return (err == null) ? null : new String[] {err};
		}
	}

}
