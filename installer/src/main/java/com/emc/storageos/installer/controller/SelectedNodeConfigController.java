/**
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import charva.awt.Component;
import charva.awt.event.ItemEvent;
import charva.awt.event.ItemListener;
import charvax.swing.JRadioButton;

import com.emc.storageos.services.data.Configuration;
import com.emc.storageos.installer.util.ClusterType;
import com.emc.storageos.services.util.InstallerConstants;
import com.emc.storageos.installer.widget.SelectButtonPanel;

/**
 * Class implements the control for Node configuration page for rejoin case.
 *
 */
public class SelectedNodeConfigController implements IConfigPanelController {
	private static final Logger log = LoggerFactory.getLogger(SelectedNodeConfigController.class);
	private int nodeCount = 0;
	private String nodeId;
	private Configuration config;
	private SelectButtonPanel panel;
	
	public SelectedNodeConfigController(Configuration config, SelectButtonPanel panel) {
		this.panel = panel;
		this.config = config;
		setupEventListener();
	}
	
	private void setupEventListener() {
		panel.selectList1ItemListener(new SelectNodeCountListener());
		panel.selectList2ItemListener(new SelectNodeIdListener());
	}

	class SelectNodeCountListener implements ItemListener {
		@Override
		public void itemStateChanged(ItemEvent ie) {
			int statechange = ie.getStateChange();
			Component source = (Component) ie.getSource();
			if (statechange == ItemEvent.SELECTED) {
				String buttonLabel = ((JRadioButton) source).getText();
				
				if (buttonLabel.equals(ClusterType.NODE_COUNT_1.getLabel())) {
					nodeCount = ClusterType.NODE_COUNT_1.getCount();
				} else if (buttonLabel.equals(ClusterType.NODE_COUNT_3.getLabel())) {
					nodeCount = ClusterType.NODE_COUNT_3.getCount();
				} else if (buttonLabel.equals(ClusterType.NODE_COUNT_5.getLabel())) {
					nodeCount = ClusterType.NODE_COUNT_5.getCount();
				}
				resetVisibility();
				resetEnableDisable();
			}
		}

		/*
		 * Disable/gray out the node id already taken
		 */
		private void resetEnableDisable() {
			// get the node already taken and disable it
			for (int i = 0; i < nodeCount; i++) {
				JRadioButton node = panel.getButtonList2().get(i);
				if (config.getAliveNodes().contains(node.getText())) {
					// this node already taken, disable it
					node.setEnabled(false);
				} else {
					node.setEnabled(true);
				}
			}
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
			for (int i = 0; i < nodeCount; i++) {
				panel.getButtonList2().get(i).setVisible(true);
			}
		}
	}
	
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

	@Override
	public String[] configurationIsCompleted() {
		String err = null;
		if (nodeId == null) {
			err =  InstallerConstants.CLUSTER_CONFIG_WARN_MSG;
		} else {
			log.info("Saving node id {} into the config", nodeId);
			config.setNodeId(nodeId);
		}
		return (err == null) ? null : new String[] {err};
	}

}
