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

import charva.awt.Component;
import charva.awt.event.ItemEvent;
import charva.awt.event.ItemListener;
import charvax.swing.JRadioButton;

import com.emc.storageos.services.util.Configuration;
import com.emc.storageos.installer.util.ClusterType;
import com.emc.storageos.installer.widget.SelectButtonPanel;

/**
 * Class implements the control for Node configuration page.
 *
 */
public class NewNodeConfigController implements IConfigPanelController {
    private int nodeCount = 0;
    private String nodeId;
    private Configuration config;
    private SelectButtonPanel panel;

    public NewNodeConfigController(Configuration config, SelectButtonPanel panel) {
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
                resetNodeIdConfig(nodeCount);
            }
        }

        private void resetNodeIdConfig(int nodeCount) {
            JRadioButton node1 = panel.getButtonList2().get(0);
            JRadioButton node2 = panel.getButtonList2().get(1);
            JRadioButton node3 = panel.getButtonList2().get(2);
            JRadioButton node4 = panel.getButtonList2().get(3);
            JRadioButton node5 = panel.getButtonList2().get(4);
            switch (nodeCount) {
                case 1:
                    node2.setVisible(false);
                    node3.setVisible(false);
                    node4.setVisible(false);
                    node5.setVisible(false);
                    break;
                case 3:
                    node2.setVisible(true);
                    node3.setVisible(true);
                    node4.setVisible(false);
                    node5.setVisible(false);
                    break;
                case 5:
                    node2.setVisible(true);
                    node3.setVisible(true);
                    node4.setVisible(true);
                    node5.setVisible(true);
                    break;
            }
            node1.setSelected(true);
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
        config.setNodeCount(nodeCount);
        config.setNodeId(nodeId);
        return (err == null) ? null : new String[] { err };
    }
}
