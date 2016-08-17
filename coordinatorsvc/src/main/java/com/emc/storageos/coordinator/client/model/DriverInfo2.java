package com.emc.storageos.coordinator.client.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jsoup.helper.StringUtil;

import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;

public class DriverInfo2 {

    public static final String CONFIG_ID = "global";
    public static final String CONFIG_KIND = "storagesystemdriver";

    private static final String KEY_DRIVERS = "drivers";
    private static final String KEY_INIT_NODE = "initNode";
    private static final String KEY_FINISH_NODES = "finishNodes";

    private List<String> drivers = new ArrayList<String>();
    private String initNode;
    // Node name format $siteid_$nodeid, e.g. "1b2fe070-5e3c-11e6-29b3-f8319a30ed54_vipr1"
    private List<String> finishNodes = new ArrayList<String>();

    public DriverInfo2() {
    }

    public DriverInfo2(Configuration config) {
        if (config != null) {
            fromConfiguration(config);
        }
    }
    public Configuration toConfiguration() {
        ConfigurationImpl config = new ConfigurationImpl();
        config.setKind(CONFIG_KIND);
        config.setId(CONFIG_ID);
        if (drivers != null && !drivers.isEmpty()) {
            config.setConfig(KEY_DRIVERS, StringUtil.join(drivers, ","));
        }
        if (initNode != null && !initNode.isEmpty()) {
            config.setConfig(KEY_INIT_NODE, initNode);
        }
        if (finishNodes != null || !finishNodes.isEmpty()) {
            config.setConfig(KEY_FINISH_NODES, StringUtil.join(finishNodes, ","));
        }
        return config;
    }

    private void fromConfiguration(Configuration config) {
        if (!CONFIG_KIND.equals(config.getKind())) {
            throw new IllegalArgumentException("Unexpected configuration kind for DriverInfo");
        }
        try {
            String driversStr = config.getConfig(KEY_DRIVERS);
            if (driversStr != null && !driversStr.isEmpty()) {
                drivers = Arrays.asList(driversStr.split(","));
            }
            initNode = config.getConfig(KEY_INIT_NODE);
            String finishNodesStr = config.getConfig(KEY_FINISH_NODES);
            if (finishNodesStr != null && !finishNodesStr.isEmpty()) {
                finishNodes = Arrays.asList(finishNodesStr.split(","));
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unrecognized configuration data for DriverInfo", ex);
        }
    }


    public List<String> getDrivers() {
        return drivers;
    }

    public void setDrivers(List<String> drivers) {
        this.drivers = drivers;
    }

    public String getInitNode() {
        return initNode;
    }

    public void setInitNode(String initNode) {
        this.initNode = initNode;
    }

    public List<String> getFinishNodes() {
        return finishNodes;
    }

    public void setFinishNodes(List<String> finishNodes) {
        this.finishNodes = finishNodes;
    }
}
