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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.model.property.PropertyConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.installer.util.ClusterType;
import com.emc.storageos.installer.util.InstallerOperation;
import com.emc.storageos.installer.util.InstallerConstants;
import com.emc.storageos.installer.util.InstallerUtil;
import com.emc.storageos.services.util.Configuration;
import com.emc.storageos.services.util.PlatformUtils;
import com.emc.storageos.services.util.ServerProbe;
import com.emc.storageos.installer.widget.BasePanel;
import com.emc.storageos.installer.widget.DisplayPanel;
import com.emc.storageos.installer.widget.InstallerWizard;
import com.emc.storageos.installer.widget.SelectButtonPanel;
import com.emc.storageos.installer.widget.SelectListPanel;
import com.emc.storageos.installer.widget.TabbedPanel;
import com.emc.storageos.installer.widget.TextAreaPanel;
import com.emc.storageos.installer.widget.TextInputPanel;

public class InstallerManager {
    private static final Logger log = LoggerFactory.getLogger(InstallerManager.class);
    private Map<String, IConfigPanelController> controllerMap;
    private Configuration config;
    private boolean devMode = false;
    private String bootMode;
    private InstallerWizard wizard;
    private int nodeCountConfigured;
    private String releaseVersion;
    private Set<Configuration> availableClusters;
    private Configuration localConfig;
    private ConfigType configType;

    private enum ConfigType {
        INSTALL,
        INITIAL_CONFIG,
        RE_CONFIG,
        REDEPLOY
    }

    public InstallerManager(String releaseVersion, String bootMode) {
        this.releaseVersion = releaseVersion;
        this.bootMode = bootMode;
        this.config = new Configuration();
        init();
    }

    private void init() {
        config.setScenario(bootMode);
        controllerMap = new HashMap<String, IConfigPanelController>();
        setConfigType();
    }

    /*
     * Set config type based on boot mode user entered and whether there is local config.
     */
    private void setConfigType() {
        if (bootMode.equals(PropertyConstants.INSTALL_MODE)) {
            configType = ConfigType.INSTALL;
        } else if (bootMode.equals(PropertyConstants.REDEPLOY_MODE)) {
            configType = ConfigType.REDEPLOY;
        } else if (bootMode.equals(PropertyConstants.CONFIG_MODE)) {
            Configuration local = getLocalConfiguration();
            if (local != null) {
                configType = ConfigType.RE_CONFIG;
            } else {
                configType = ConfigType.INITIAL_CONFIG;
            }
        }
        log.info("Installer is running in {} case", configType);
    }

    public void setWizard(InstallerWizard wizard) {
        this.wizard = wizard;
    }

    public void setBootMode(String bootMode) {
        this.bootMode = bootMode;
    }

    /*
     * Check if network interface page needs to be skipped.
     * 
     * @return true if only one network interface discovered; false otherwise
     */
    private boolean skipNetworkIfConfig() {
        // if only one netif available, skip user config, save the data to Configuration directly
        String[] ifs = ServerProbe.getInstance().getNetworkInterfaces();
        if (ifs.length == 1) {
            config.getHwConfig().put(PropertyConstants.PROPERTY_KEY_NETIF, ifs[0]);
            return true;
        } else {
            return false;
        }
    }

    /*
     * Check if disk selection page can be skipped.
     * 
     * @return true if only one disk discovered or if installer is running on config mode
     * false otherwise
     */
    private boolean skipDiskConfig() {
        /*
         * 1. In Hypervisor platform ESXi, Hyper-V or KVM etc, ViPR always has four disks.
         * /dev/sdc is always "data" disk, user need not to select disk for installing ViPR.
         * 2. In Baremetal/Native installed env, user needs to select a whole disk to install ViPR
         * TODO: Address baremetal/native env later.
         */
        boolean isNative = false;
        if (!isNative) {
            if (config.isConfigMode() ||
                    config.isRedeployMode() && InstallerOperation.getBootDeviceType().equals("harddisk")) {
                // In non-native installed env, we do not ask user to select disk.
                // And we only check "data" disk size (meet minimum requirement and same etc.)
                String diskName = "/dev/sdc";
                if (checkDiskCapacityForMinimumRequirement(diskName)) {
                    config.getHwConfig().put(PropertyConstants.PROPERTY_KEY_DISK, diskName);
                    config.getHwConfig().put(PropertyConstants.PROPERTY_KEY_DISK_CAPACITY,
                            ServerProbe.getInstance().getDiskCapacity(diskName));
                    return true;
                }
            }
        }

        // if only one disk available & meet minimum size requirement,
        // skip user config, save the data to Configuration directly
        String[] list = InstallerOperation.getDiskInfo();
        if (list.length == 1) {
            String diskStr = list[0];
            String diskName = InstallerOperation.parseDiskString(diskStr);
            if (checkDiskCapacityForMinimumRequirement(diskName) && !diskHasPartition(diskName)) {
                config.getHwConfig().put(PropertyConstants.PROPERTY_KEY_DISK, diskName);
                config.getHwConfig().put(PropertyConstants.PROPERTY_KEY_DISK_CAPACITY,
                        ServerProbe.getInstance().getDiskCapacity(diskName));
                return true;
            }
        } else {
            return false;
        }
        return false;
    }

    /*
     * Check disk for partition.
     * 
     * @param disk
     * 
     * @return true if already has partition; false otherwise
     */
    private boolean diskHasPartition(String diskName) {
        if (ServerProbe.getInstance().hasViPRPartition(diskName)) {
            log.warn("Disk {} has ViPR partition already", diskName);
            return true;
        }
        return false;
    }

    private boolean checkDiskCapacityForMinimumRequirement(String disk) {
        if (!ServerProbe.getInstance().diskMetMiniumSizeReq(disk)) {
            log.warn("Disk {} does not meet minimum capacity requirement", disk);
            return false;
        }
        return true;
    }

    /*
     * Check if this is new installation in install mode
     * 
     * @return true if it is new installation, false otherwise
     */
    private boolean isNewInstall() {
        boolean ret = true;
        InstallClusterController controller = (InstallClusterController) controllerMap
                .get(InstallerConstants.CLUSTER_PANEL_ID);
        if (controller != null) {
            ret = controller.isNewInstall();
        }
        log.info("New install flag is set to {}", ret);
        return ret;
    }

    /*
     * Check if this is new installation in install mode
     * 
     * @return true if it is new installation, false otherwise
     */
    private boolean isNewConfig() {
        boolean ret = true;
        if (configType.equals(ConfigType.INITIAL_CONFIG)) {
            InitialConfigController controller = (InitialConfigController) controllerMap
                    .get(InstallerConstants.CLUSTER_PANEL_ID);
            if (controller != null) {
                ret = controller.isNewConfig();
            }
        } else {
            ConfigClusterController controller = (ConfigClusterController) controllerMap
                    .get(InstallerConstants.CLUSTER_PANEL_ID);
            if (controller != null) {
                ret = controller.isNewConfig();
            }
        }
        log.info("New config flag is set to {}", ret);
        return ret;
    }

    /*
     * Check if selected configuration has the same network addresses as local one.
     * 
     * @return true if they are same; othersise false
     */
    private boolean isNetworkSettingSame() {
        boolean ret = false;
        Configuration selected = null;
        Configuration local = getLocalConfiguration();
        ConfigClusterController controller = (ConfigClusterController) controllerMap
                .get(InstallerConstants.CLUSTER_PANEL_ID);
        if (controller != null) {
            selected = controller.getSelectedConfig();
        }

        if (selected != null && local != null) {
            // this error should not happen, as we check when user select the cluster
            if (selected.getNodeCount() != local.getNodeCount()) {
                log.error("Selected config node count {} is different from local config node count {}",
                        selected.getNodeCount(), local.getNodeCount());
                return false;
            }

            if (selected.getNetworkIpv4Config().equals(local.getNetworkIpv4Config())
                    && selected.getNetworkIpv6Config().equals(local.getNetworkIpv6Config())) {
                log.info("selected config network settings are the same as local one (first node re-config)");
                ret = true;
            } else {
                log.info("selected config network settings is different from local one (not first node re-config)");
                ret = false;
            }
        }
        return ret;
    }

    /*
     * Get the next panel id based on current panel id and some logics (only one selection,
     * different boot modes, new install/config node etc.).
     * 
     * @param cId the current panel id
     * 
     * @return the next panel id
     */
    private String getNextPanelId(String cId) {
        String nId = null;
        switch (cId) {
            case InstallerConstants.NETWORK_INT_PANEL_ID:
                nId = InstallerConstants.CLUSTER_PANEL_ID;
                break;
            case InstallerConstants.CLUSTER_PANEL_ID:
                if (configType.equals(ConfigType.RE_CONFIG)) {
                    // this is re-config mode check network setting of local and selected one
                    if (isNetworkSettingSame()) {
                        // if same, it is the first node of re-config, goto network page
                        nId = InstallerConstants.NETWORK_PANEL_ID;
                    } else {
                        // if diff, it is not the first one of re-config, goto summary page
                        // copy network info from selected config to local config as its new config
                        nId = InstallerConstants.SUMMARY_PANEL_ID;
                    }
                } else {
                    nId = InstallerConstants.NODE_PANEL_ID;
                }
                break;
            case InstallerConstants.NODE_PANEL_ID:
                if (config.isRedeployMode()) {
                    if (skipDiskConfig()) {
                        nId = InstallerConstants.SUMMARY_PANEL_ID;
                    } else {
                        nId = InstallerConstants.DISK_PANEL_ID;
                    }
                } else if (config.isInstallMode()) {
                    if (isNewInstall()) {
                        nId = InstallerConstants.NETWORK_PANEL_ID;
                    } else {
                        nId = InstallerConstants.SUMMARY_PANEL_ID;
                    }
                } else {
                    if (isNewConfig()) {
                        nId = InstallerConstants.NETWORK_PANEL_ID;
                    } else {
                        nId = InstallerConstants.SUMMARY_PANEL_ID;
                    }
                }
                break;
            case InstallerConstants.NETWORK_PANEL_ID:
                if (skipDiskConfig()) {
                    nId = InstallerConstants.SUMMARY_PANEL_ID;
                } else {
                    nId = InstallerConstants.DISK_PANEL_ID;
                }
                break;
            case InstallerConstants.DISK_PANEL_ID:
                nId = InstallerConstants.SUMMARY_PANEL_ID;
                break;
            case InstallerConstants.SUMMARY_PANEL_ID:
                nId = null;
                break;
        }
        log.info("getNextPanelId: " + cId + "->" + nId);
        return nId;
    }

    public void setDevMode(boolean b) {
        this.devMode = b;
    }

    public void setReleaseVersion(String version) {
        this.releaseVersion = version;
    }

    /**
     * Get the controller map.
     * 
     * @return the controller map
     */
    public Map<String, IConfigPanelController> getControllers() {
        return this.controllerMap;
    }

    /**
     * Create panel for the id.
     * 
     * @param id the panel id
     * @return the panel for the id
     */
    public BasePanel createPanel(String id) {
        if (id == null) {
            return null;
        }
        log.info("Creating panel with id: {}", id);
        BasePanel panel = null;
        switch (id) {
            case InstallerConstants.CLUSTER_PANEL_ID:
                panel = createClusterPanel();
                break;
            case InstallerConstants.NODE_PANEL_ID:
                panel = createNodePanel();
                break;
            case InstallerConstants.NETWORK_INT_PANEL_ID:
                panel = createNetIfPanel();
                break;
            case InstallerConstants.NETWORK_PANEL_ID:
                panel = createNetworkPanel();
                break;
            case InstallerConstants.DISK_PANEL_ID:
                panel = createDiskPanel();
                break;
            case InstallerConstants.SUMMARY_PANEL_ID:
                panel = createConfirmationPanel();
                panel.setLastPage(true);
                break;
            case "ERROR_PANEL_ID":
                panel = createErrorPanel();
                panel.setLastPage(true);
                break;
        }
        return panel;
    }

    /**
     * Create the first panel with the id.
     * 
     * @param id the first panel id
     * @return the first panel
     */
    public BasePanel getFirstPanel(String id) {
        String fId = id;
        if (id.equals(InstallerConstants.NETWORK_INT_PANEL_ID) && skipNetworkIfConfig()) {
            Set<Configuration> clusters = getAllClusterConfigurations();
            if (clusters == null || clusters.isEmpty()) {
                if (config.isRedeployMode()) {
                    fId = InstallerConstants.ERROR_PANEL_ID;
                } else {
                    // skip the cluster page
                    // check if current node meet minimum hw (cpu, memory and disk size) requirement
                    if (!ServerProbe.getInstance().isMetMinimumRequirement()) {
                        fId = InstallerConstants.ERROR_PANEL_ID;
                    } else {
                        fId = InstallerConstants.NODE_PANEL_ID;
                    }
                }
            } else {
                fId = InstallerConstants.CLUSTER_PANEL_ID;
            }
        }
        BasePanel panel = createPanel(fId);
        panel.setFirstPage(true);
        return panel;
    }

    /**
     * Get the next panel when Next button pressed. First find the next panel based
     * on some logics, then create the specific panel.
     * 
     * @param current the current panel
     * @return the next panel
     */
    public BasePanel getNextPanel(BasePanel current) {
        BasePanel next;
        if (current.getNext() == null) {
            // this view has not been created/displayed before
            // 1. find the next id with some logics e.g. skip if only one choice
            // 2. create panel based on id
            String nextId = getNextPanelId(current.getId());
            next = createPanel(nextId);
            // set order
            current.setNext(next);
            if (next != null) {
                next.setPrevious(current);
            }
        } else {
            // this view has been created/displayed before, so the next pointer should be set before
            next = loadNextPanel(current);
        }
        return next;
    }

    /*
     * Load the next panel. The next panel has been created/displayed before (the next pointer
     * is set in current panel). But in the special cases below, a new panel needs to be created
     * if it depends on node count or cluster selection).
     * 
     * @param current the current panel
     * 
     * @return the next panel
     */
    private BasePanel loadNextPanel(BasePanel current) {
        BasePanel next = current.getNext();
        if (current.getId().equals(InstallerConstants.CLUSTER_PANEL_ID)) {
            // recalculate the next path based on new install or not
            String nextId = getNextPanelId(current.getId());
            next = createPanel(nextId);
            // set order
            current.setNext(next);
            if (next != null) {
                next.setPrevious(current);
            }
            log.info("loadNextPanel: cId: {}, nId: {}", current.getId(), nextId);
        } else {
            next = current.getNext();
            // need to check if the panel has dependency on node count,
            // previous created panel may not useful anymore if node count changed
            if (next.getId() == InstallerConstants.NETWORK_PANEL_ID) {
                if (config.getNodeCount() != nodeCountConfigured) {
                    // user select diff node count when going back
                    next = createPanel(next.getId());
                    // set order
                    current.setNext(next);
                    if (next != null) {
                        next.setPrevious(current);
                    }
                }
            } else if (next.getId() == InstallerConstants.SUMMARY_PANEL_ID) {
                // re-create summary page as the data may get modified
                next = createPanel(next.getId());
                // set order
                current.setNext(next);
                if (next != null) {
                    next.setPrevious(current);
                }
            }
        }
        return next;
    }

    public BasePanel getPriviousPanel(BasePanel current) {
        return current.getPrevious();
    }

    /*
     * Get the installer boot mode (install/config/redeploy) displayed along with page title.
     * 
     * @return the String to display boot mode
     */
    private String getScenarioDisplay() {
        String scenario = config.getScenario();
        String displayScenario = null;
        if (scenario.equals(PropertyConstants.INSTALL_MODE)) {
            displayScenario = "Install";
        } else if (scenario.equals(PropertyConstants.CONFIG_MODE)) {
            displayScenario = "Configuration";
        } else if (scenario.equals(PropertyConstants.REDEPLOY_MODE)) {
            displayScenario = "Re-deployment";
        }
        return displayScenario == null ? "" : " (" + displayScenario + ")";
    }

    /*
     * Create cluster selection panel
     * 
     * @return the cluster panel
     */
    private BasePanel createClusterPanel() {
        String id = InstallerConstants.CLUSTER_PANEL_ID;
        Map<Object, Object> data = new HashMap<Object, Object>();
        data.put(InstallerConstants.PANEL_TITLE_KEY, InstallerConstants.CLUSTER_PANEL_TITLE + getScenarioDisplay());
        data.put(InstallerConstants.PANEL_INST_KEY, InstallerConstants.CLUSTER_PANEL_INST);

        // list available cluster configuration over the network user selected
        Set<Configuration> availableClusters = getAllClusterConfigurations();
        // get the display list for cluster page
        List<String> displayList = getClusterDisplayList(availableClusters);

        SelectButtonPanel panel = new SelectButtonPanel(wizard, InstallerConstants.CLUSTER_CONFIG_LABEL, displayList);
        if (config.isInstallMode()) {
            InstallClusterController controller = new InstallClusterController(config, panel, availableClusters);
            controllerMap.put(id, controller);
        } else if (configType.equals(ConfigType.INITIAL_CONFIG)) {
            InitialConfigController controller = new InitialConfigController(config, panel, availableClusters);
            controllerMap.put(id, controller);
        } else if (configType.equals(ConfigType.RE_CONFIG)) {
            ConfigClusterController controller = new ConfigClusterController(config, panel, availableClusters, localConfig);
            controllerMap.put(id, controller);
        } else if (configType.equals(ConfigType.REDEPLOY)) {
            RejoinClusterController controller = new RejoinClusterController(config, panel, availableClusters);
            controllerMap.put(id, controller);
        } else {
            log.error("Installer is running in unknown boot mode: {}", configType);
        }
        data.put(InstallerConstants.PANEL_POSITION_CENTER, panel);
        return new BasePanel(id, data);
    }

    /*
     * Get all available configurations over the network and from local disk if in config mode.
     * 
     * @return all the available configurations found in a set
     */
    private Set<Configuration> getAllClusterConfigurations() {
        if (availableClusters != null) {
            return availableClusters;
        }
        // if never checked before do scan and get local if applicable
        availableClusters = InstallerUtil.scanClusters(config.getHwConfig()
                .get(PropertyConstants.PROPERTY_KEY_NETIF), releaseVersion, config.getScenario());
        // if config mode, add configuration from local disk
        if (config.isConfigMode()) {
            Configuration localConfig = getLocalConfiguration();
            if (localConfig != null) {
                availableClusters.add(localConfig);
                // save local probed configuration to current config
                config.setNodeCount(localConfig.getNodeCount());
                config.setNodeId(localConfig.getNodeId());
                config.getHwConfig().putAll(localConfig.getHwConfig());

                // clear network setting before putting new ones (node count may changed)
                config.getNetworkIpv4Config().clear();
                config.getNetworkIpv6Config().clear();
                config.getNetworkIpv4Config().putAll(localConfig.getNetworkIpv4Config());
                config.getNetworkIpv6Config().putAll(localConfig.getNetworkIpv6Config());
            }
        }
        return availableClusters;
    }

    /*
     * Get local configuration from ovfenv partition and real h/w.
     * 
     * @return the local configuration
     */
    private Configuration getLocalConfiguration() {
        if (localConfig != null) {
            return localConfig;
        }
        log.info("Getting local configuration ...");
        try {
            // 1. Probe empty ovfenv partition
            if (0 == InstallerOperation.probeOvfenvEmptyPartition()) {
                log.info("Probed empty ovfenv partition");
                return localConfig;
            }

            // 2. Collect local configuration
            localConfig = PlatformUtils.getLocalConfiguration();
            log.info("Local found config: {}/{}", localConfig.getNetworkVip(), localConfig.getNodeId());
        } catch (Exception e) {
            log.error("Failed to get local configuration with exception {}", e.getMessage());
        }
        return localConfig;
    }

    /*
     * Get the display list for the available clusters with its unique IPv4 VIP address
     * and its cluster type.
     * 
     * @param configs set of cluster configurations
     * 
     * @return a list of display labels represents the clusters
     */
    private List<String> getClusterDisplayList(Set<Configuration> configs) {
        Set<String> names = new HashSet<String>();
        if (configs != null && !configs.isEmpty()) {
            for (Configuration config : configs) {
                String vip = config.getNetworkVip();

                // cluster label format is: "<VIP> [<cluster type>]"
                StringBuilder clusterInfo = new StringBuilder();
                clusterInfo.append(vip);
                clusterInfo.append(InstallerConstants.CLUSTER_LABEL_DELIMITER);
                clusterInfo.append("[");
                clusterInfo.append(getClusterConfigType(config.getNodeCount())).append("] ");
                names.add(clusterInfo.toString());
            }
        }
        List<String> list = new ArrayList<String>(names);
        if (config.isInstallMode()) {
            list.add(InstallerConstants.CLUSTER_CONFIG_NEW_INSTALL_LABEL);
        } else if (configType.equals(ConfigType.INITIAL_CONFIG)) {
            list.add(InstallerConstants.CLUSTER_CONFIG_NEW_CONFIG_LABEL);

        }
        // if list is empty, add warning msg to user
        if (list.isEmpty()) {
            list.add("Cluster is empty");
        }
        return list;
    }

    /*
     * Create Node selection panel
     * 
     * @return the node panel
     */
    private BasePanel createNodePanel() {
        String id = InstallerConstants.NODE_PANEL_ID;
        Map<Object, Object> data = new HashMap<Object, Object>();
        data.put(InstallerConstants.PANEL_TITLE_KEY, InstallerConstants.NODE_PANEL_TITLE + getScenarioDisplay());
        data.put(InstallerConstants.PANEL_INST_KEY, InstallerConstants.NODE_PANEL_INST);

        SelectButtonPanel panel = new SelectButtonPanel(wizard,
                InstallerConstants.NODE_COUNT_CONFIG_LABEL,
                InstallerConstants.NODE_ID_CONFIG_LABEL,
                getNodeCountList(),
                getNodeIdList());
        if (config.isRedeployMode()) {
            data.put(InstallerConstants.PANEL_POSITION_CENTER, panel);
            SelectedNodeConfigController nodeController = new SelectedNodeConfigController(config, panel);
            controllerMap.put(id, nodeController);
        } else {
            data.put(InstallerConstants.PANEL_POSITION_CENTER, panel);
            if (configType.equals(ConfigType.INSTALL) && isNewInstall()
                    || configType.equals(ConfigType.INITIAL_CONFIG) && isNewConfig()) {
                NewNodeConfigController nodeController = new NewNodeConfigController(config, panel);
                controllerMap.put(id, nodeController);
            } else {
                SelectedNodeConfigController nodeController = new SelectedNodeConfigController(config, panel);
                controllerMap.put(id, nodeController);
            }
        }
        return new BasePanel(id, data);
    }

    private List<String> getNodeCountList() {
        ArrayList<String> list = new ArrayList<String>();
        if (config.getNodeCount() != 0) {
            list.add(getClusterConfigType(config.getNodeCount()));
        } else {
            if (devMode) {
                list.add(ClusterType.NODE_COUNT_1.getLabel());
            }
            list.add(ClusterType.NODE_COUNT_3.getLabel());
            list.add(ClusterType.NODE_COUNT_5.getLabel());
        }
        return list;
    }

    private List<String> getNodeIdList() {
        ArrayList<String> list = new ArrayList<String>();
        list.add(InstallerConstants.NODE_ID_VIPR1);
        list.add(InstallerConstants.NODE_ID_VIPR2);
        list.add(InstallerConstants.NODE_ID_VIPR3);
        list.add(InstallerConstants.NODE_ID_VIPR4);
        list.add(InstallerConstants.NODE_ID_VIPR5);
        return list;
    }

    /*
     * Create Network Interface selection panel
     * 
     * @return the network interface panel
     */
    private BasePanel createNetIfPanel() {
        // get the list of network i/f
        String[] ifs = ServerProbe.getInstance().getNetworkInterfaces();
        String id = InstallerConstants.NETWORK_INT_PANEL_ID;
        Map<Object, Object> data = new HashMap<Object, Object>();
        data.put(InstallerConstants.PANEL_TITLE_KEY, InstallerConstants.NETWOK_INT_PANEL_TITLE + getScenarioDisplay());
        data.put(InstallerConstants.PANEL_INST_KEY, InstallerConstants.NETWORK_INT_PANEL_INST);
        SelectListPanel netIfPanel = new SelectListPanel(wizard, InstallerConstants.NETWORK_INT_LABEL, ifs);
        data.put(InstallerConstants.PANEL_POSITION_CENTER, netIfPanel);
        NetworkInterfaceConfigController netIfsController = new NetworkInterfaceConfigController(config, netIfPanel);
        controllerMap.put(id, netIfsController);
        return new BasePanel(id, data);
    }

    /*
     * Create Network configuration panel
     * 
     * @return the Network panel
     */
    private BasePanel createNetworkPanel() {
        String id = InstallerConstants.NETWORK_PANEL_ID;
        Map<Object, Object> data = new HashMap<Object, Object>();
        data.put(InstallerConstants.PANEL_TITLE_KEY, InstallerConstants.NETWORK_PANEL_TITLE + getScenarioDisplay());
        data.put(InstallerConstants.PANEL_INST_KEY, InstallerConstants.NETWORK_PANEL_INST);
        TextInputPanel ipv4Panel = new TextInputPanel(InstallerConstants.IPV4_PANEL_INSTR,
                createIPv4PanelInputData(), 15);
        TextInputPanel ipv6Panel = new TextInputPanel(InstallerConstants.IPV6_PANEL_INSTR,
                createIPv6PanelInputData(), 45);
        TabbedPanel netPanel = new TabbedPanel(InstallerConstants.IPV4_PANEL_TITLE,
                InstallerConstants.IPV6_PANEL_TITLE, ipv4Panel, ipv6Panel);
        data.put(InstallerConstants.PANEL_POSITION_CENTER, netPanel);
        NetworkConfigController networkConfigController = new NetworkConfigController(config, ipv4Panel, ipv6Panel);
        controllerMap.put(id, networkConfigController);
        return new BasePanel(id, data);
    }

    private LinkedHashMap<String, String> createIPv4PanelInputData() {
        int nodecount = config.getNodeCount();
        nodeCountConfigured = nodecount;
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        if (configType.equals(ConfigType.RE_CONFIG)) {
            map.putAll(InstallerUtil.getIpv4DisplayMap(config.getNetworkIpv4Config()));
        } else {
            for (int i = 0; i < nodecount; i++) {
                map.put(String.format(InstallerConstants.DISPLAY_LABEL_IPV4_NODE_ADDR, i + 1), PropertyConstants.IPV4_ADDR_DEFAULT);
            }
            map.put(InstallerConstants.DISPLAY_LABEL_IPV4_VIP, PropertyConstants.IPV4_ADDR_DEFAULT);
            map.put(InstallerConstants.DISPLAY_LABEL_IPV4_NETMASK, PropertyConstants.NETMASK_DEFAULT);
            map.put(InstallerConstants.DISPLAY_LABEL_IPV4_GATEWAY, PropertyConstants.IPV4_ADDR_DEFAULT);
        }
        return map;
    }

    private LinkedHashMap<String, String> createIPv6PanelInputData() {
        int nodecount = config.getNodeCount();
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(nodecount);
        if (configType.equals(ConfigType.RE_CONFIG)) {
            map.putAll(InstallerUtil.getIpv6DisplayMap(config.getNetworkIpv6Config()));
        } else {
            for (int i = 0; i < nodecount; i++) {
                map.put(String.format(InstallerConstants.DISPLAY_LABEL_IPV6_NODE_ADDR, i + 1), PropertyConstants.IPV6_ADDR_DEFAULT);
            }
            map.put(InstallerConstants.DISPLAY_LABEL_IPV6_VIP, PropertyConstants.IPV6_ADDR_DEFAULT);
            map.put(InstallerConstants.DISPLAY_LABEL_IPV6_PREFIX, PropertyConstants.IPV6_PREFIX_LEN_DEFAULT);
            map.put(InstallerConstants.DISPLAY_LABEL_IPV6_GATEWAY, PropertyConstants.IPV6_ADDR_DEFAULT);
        }
        return map;
    }

    /*
     * Create Disk selection panel
     * 
     * @return the disk panel
     */
    private BasePanel createDiskPanel() {
        String[] list = InstallerOperation.getDiskInfo();
        String id = InstallerConstants.DISK_PANEL_ID;
        Map<Object, Object> data = new HashMap<Object, Object>();
        data.put(InstallerConstants.PANEL_TITLE_KEY, InstallerConstants.DISK_PANEL_TITLE + getScenarioDisplay());
        data.put(InstallerConstants.PANEL_INST_KEY, InstallerConstants.DISK_PANEL_INST);
        SelectListPanel diskPanel = new SelectListPanel(wizard, InstallerConstants.DISK_CONFIG_LABEL, list);
        data.put(InstallerConstants.PANEL_POSITION_CENTER, diskPanel);
        DiskConfigController diskController = new DiskConfigController(config, diskPanel);
        controllerMap.put(id, diskController);
        return new BasePanel(id, data);
    }

    /*
     * Create Confirmation panel
     * 
     * @return the Confirmation panel
     */
    private BasePanel createConfirmationPanel() {
        String id = InstallerConstants.SUMMARY_PANEL_ID;
        Map<Object, Object> data = new HashMap<Object, Object>();
        data.put(InstallerConstants.PANEL_TITLE_KEY, InstallerConstants.SUMMARY_PANEL_TITLE + getScenarioDisplay());
        data.put(InstallerConstants.PANEL_INST_KEY, null);
        String[] labels = getOperationInstructions();
        DisplayPanel panel = new DisplayPanel(wizard, InstallerConstants.SUMMARY_PROPERTY_LABEL, labels[0],
                labels[1], getConfigurationPropertyToDisplay(), getStartButtonLabel());

        data.put(InstallerConstants.PANEL_POSITION_CENTER, panel);
        ConfirmationController controller = new ConfirmationController(config, panel, releaseVersion);
        controllerMap.put(id, controller);
        return new BasePanel(id, data);
    }

    private String[] getOperationInstructions() {
        String[] insts;
        if (configType.equals(ConfigType.REDEPLOY)) {
            insts = new String[] { InstallerConstants.REDEPLOY_INST_1, InstallerConstants.REDEPLOY_INST_2 };
        } else if (configType.equals(ConfigType.INITIAL_CONFIG)) {
            insts = new String[] { "", InstallerConstants.INITIAL_CONFIG_INST };
        } else if (configType.equals(ConfigType.RE_CONFIG)) {
            insts = new String[] { InstallerConstants.RECONFIG_INST_1, InstallerConstants.RECONFIG_INST_2 };
        } else {
            insts = new String[] { InstallerConstants.INSTALL_INST_1, InstallerConstants.INSTALL_INST_2 };
        }
        return insts;
    }

    private String getStartButtonLabel() {
        String label;
        if (configType.equals(ConfigType.REDEPLOY)) {
            label = InstallerConstants.BUTTON_REDEPLOY;
        } else if (configType.equals(ConfigType.INITIAL_CONFIG) || (configType.equals(ConfigType.RE_CONFIG))) {
            label = InstallerConstants.BUTTON_CONFIG;
        } else {
            label = InstallerConstants.BUTTON_INSTALL;
        }
        return label;
    }

    private void setLocalHardwareConfig() {
        Map<String, String> hwMap = config.getHwConfig();
        if (hwMap.size() != 5) {
            log.debug("Hardware config data. Only has {}/5 in {}",
                    hwMap.size(), hwMap);
        }

        ServerProbe serverProbe = ServerProbe.getInstance();
        // check on memory
        if (hwMap.get(PropertyConstants.PROPERTY_KEY_MEMORY_SIZE) == null) {
            String memStr = serverProbe.getMemorySize();
            log.debug("Setting mem {}", memStr);
            hwMap.put(PropertyConstants.PROPERTY_KEY_MEMORY_SIZE, memStr);
        }
        // check on cpu core
        if (hwMap.get(PropertyConstants.PROPERTY_KEY_CPU_CORE) == null) {
            String cpuCore = serverProbe.getCpuCoreNum();
            log.debug("Setting cpu core {}", cpuCore);
            hwMap.put(PropertyConstants.PROPERTY_KEY_CPU_CORE, cpuCore);
        }

        // check on disk size
        if (hwMap.get(PropertyConstants.PROPERTY_KEY_DISK) != null && hwMap.get(PropertyConstants.PROPERTY_KEY_DISK_CAPACITY) == null) {
            Map<String, String> disks = serverProbe.getDiskCapacity();
            String diskSize = disks.get(hwMap.get(PropertyConstants.PROPERTY_KEY_DISK));
            log.debug("Setting disk size {} for {}", diskSize,
                    hwMap.get(PropertyConstants.PROPERTY_KEY_DISK));
            hwMap.put(PropertyConstants.PROPERTY_KEY_DISK_CAPACITY, diskSize);
        }
        log.debug("Hardware map {}/{}", hwMap.size(), hwMap);
    }

    /*
     * Create summary data from configuration properties user configured to display.
     * 
     * @return the summary data map
     */
    private LinkedHashMap<String, String> getConfigurationPropertyToDisplay() {
        // set the hardware config data from local
        setLocalHardwareConfig();
        if (config.getHwConfig().size() != 5) {
            log.warn("Missing hardware config data. Only has {}/5 in {}",
                    config.getHwConfig().size(), config.getHwConfig());
        } else {
            log.info("Creating summary with Configuration {}",
                    config.toString());
        }
        String noValue = "NO_VALUE";
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        map.put(InstallerConstants.DISPLAY_LABEL_NETIF, config.getHwConfig().get(PropertyConstants.PROPERTY_KEY_NETIF) == null ?
                noValue : config.getHwConfig().get(PropertyConstants.PROPERTY_KEY_NETIF));
        String disk = config.getHwConfig().get(PropertyConstants.PROPERTY_KEY_DISK);
        if (disk != null)
            map.put(InstallerConstants.DISPLAY_LABEL_DISK, disk);
        map.put(InstallerConstants.DISPLAY_LABEL_NODE_COUNT, String.valueOf(config.getNodeCount()));
        map.put(InstallerConstants.DISPLAY_LABEL_NODE_ID, config.getNodeId() == null ? noValue : config.getNodeId());
        Map<String, String> ipv4map = InstallerUtil.getIpv4DisplayMap(config.getNetworkIpv4Config());
        for (String key : ipv4map.keySet()) {
            map.put("IPv4 " + key, (ipv4map.get(key) == null) ? noValue : ipv4map.get(key));
        }
        Map<String, String> ipv6map = InstallerUtil.getIpv6DisplayMap(config.getNetworkIpv6Config());
        for (String key : ipv6map.keySet()) {
            map.put("IPv6 " + key, (ipv6map.get(key) == null) ? noValue : ipv6map.get(key));
        }
        return map;
    }

    private String getClusterConfigType(int nodeCount) {
        String ret = "unknown type";
        switch (nodeCount) {
            case 1:
                ret = InstallerConstants.NODE_COUNT_1_STRING;
                break;
            case 3:
                ret = InstallerConstants.NODE_COUNT_3_STRING;
                break;
            case 5:
                ret = InstallerConstants.NODE_COUNT_5_STRING;
                break;
        }
        return ret;
    }

    /*
     * Create Error panel
     * 
     * @return the error panel
     */
    private BasePanel createErrorPanel() {
        String id = InstallerConstants.ERROR_PANEL_ID;
        Map<Object, Object> data = new HashMap<Object, Object>();
        data.put(InstallerConstants.PANEL_TITLE_KEY, InstallerConstants.ERROR_PANEL_TITLE + getScenarioDisplay());
        data.put(InstallerConstants.PANEL_INST_KEY, InstallerConstants.ERROR_PANEL_INST);
        String errMsg = getErrorMessage();
        TextAreaPanel panel = new TextAreaPanel(InstallerConstants.ERROR_LABEL, errMsg);
        data.put(InstallerConstants.PANEL_POSITION_CENTER, panel);
        return new BasePanel(id, data);
    }

    private String getErrorMessage() {
        String retStr = "Unknown Error. Please check the log at /opt/storageos/installer.log";
        if (configType.equals(ConfigType.REDEPLOY)) {
            retStr = "\n" + InstallerConstants.ERROR_MSG_NO_CLUSTER_REDEPLOY;
        } else {
            retStr = InstallerConstants.ERROR_MSG_NOT_MEET_MIN_REQ_1 + "\n" +
                    "\n" + InstallerConstants.ERROR_MSG_NOT_MEET_MIN_REQ_2 +
                    "\n" + InstallerConstants.ERROR_MSG_NOT_MEET_MIN_REQ_3 +
                    "\n" + InstallerConstants.ERROR_MSG_NOT_MEET_MIN_REQ_4;
        }
        return retStr;
    }
}
