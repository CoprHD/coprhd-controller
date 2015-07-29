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
package com.emc.storageos.installer.util;

import com.emc.storageos.model.property.PropertyConstants;

/**
 * Constants class required for installer
 */
public interface InstallerConstants {
    // Panel title, instruction and position key
    public static final String PANEL_TITLE_KEY = "TITLE";
    public static final String PANEL_INST_KEY = "INSTRUCTION";
    public static final String PANEL_POSITION_WEST = "WEST";
    public static final String PANEL_POSITION_EAST = "EAST";
    public static final String PANEL_POSITION_CENTER = "CENTER";

    // button labels
    public static final String BUTTON_NEXT = "Next";
    public static final String BUTTON_BACK = "Back";
    public static final String BUTTON_EXIT = "Exit";
    public static final String BUTTON_REBOOT = "Reboot";
    public static final String BUTTON_INSTALL = "Install";
    public static final String BUTTON_CONFIG = "Config";
    public static final String BUTTON_REDEPLOY = "Deploy";
    public static final String BUTTON_ACTION_START = "Start";
    public static final String BUTTON_CANEL = "Cancel";

    // popup dialog labels
    public static final String DIALOG_LABEL_WARNING = "Warning";
    public static final String DIALOG_LABEL_INFO = "Info";
    public static final String DIALOG_LABEL_ERROR = "Error";
    public static final String DIALOG_LABEL_CONFIRM = "Confirm";

    // Error Panel
    public static final String ERROR_PANEL_TITLE = "Installer";
    public static final String ERROR_PANEL_INST = "Please exit the installer, fix the error and retry.";
    public static final String ERROR_PANEL_ID = "ERROR_PANEL_ID";
    public static final String ERROR_LABEL = "Error";

    // Hardware Error messages
    public static final String ERROR_MSG_HW_NOT_SAME_1 = "Current node hardware is not the same as selected one!";
    public static final String ERROR_MSG_HW_NOT_SAME_2 = "Installation cannot be performed on this node. Please exit.";

    public static final String ERROR_MSG_NOT_MEET_MIN_REQ_1 = "Node does not meet minimum hardware requirements set below.";
    public static final String ERROR_MSG_NOT_MEET_MIN_REQ_2 = " - CPU core    : " + PropertyConstants.MIN_REQ_CPU_CORE;
    public static final String ERROR_MSG_NOT_MEET_MIN_REQ_3 = " - Memory size : " + PropertyConstants.MIN_REQ_MEM_SIZE + " kB";
    public static final String ERROR_MSG_NOT_MEET_MIN_REQ_4 = " - Disk size   : " + PropertyConstants.MIN_REQ_DISK_SIZE + " GB";

    // warning message if disk already have vipr partition
    public static final String WARN_MSG_DISK_HAS_VIPR_PARTITION_1 = "This disk %s might have ViPR installed already.";
    public static final String WARN_MSG_DISK_HAS_VIPR_PARTITION_2 = "Click Next to confirm if you want to proceed installation.";
    public static final String WARN_MSG_DISK_HAS_VIPR_PARTITION_3 = "Or click Exit to cancel installation.";

    public static final String ERROR_MSG_NO_CLUSTER_REDEPLOY = "Could not find an available cluster to join.";

    // Cluster selection labels
    public static final String CLUSTER_PANEL_TITLE = "Cluster Selection";
    public static final String CLUSTER_PANEL_INST = "Use TAB/ENTER key to select cluster configuration";
    public static final String CLUSTER_PANEL_ID = "CLUSTER_PANEL_ID";
    public static final String CLUSTER_CONFIG_LABEL = "Cluster VIP [type]:";
    public static final String CLUSTER_CONFIG_WARN_MSG = "Please select an available node id from the list.";
    public static final String CLUSTER_CONFIG_NEW_INSTALL_LABEL = "New Installtion";
    public static final String CLUSTER_CONFIG_NEW_CONFIG_LABEL = "New Configuration";
    public static final String CLUSTER_LABEL_DELIMITER = " ";

    // Node configuration labels
    public static final String NODE_PANEL_TITLE = "Cluster Configuration";
    public static final String NODE_PANEL_INST = "Use TAB/ENTER key to select configuration";
    public static final String NODE_PANEL_ID = "NODE_PANEL_ID";
    public static final String NODE_ID_CONFIG_LABEL = "ViPR node id:";
    public static final String NODE_COUNT_CONFIG_LABEL = "Cluster config:";
    public static final String NODE_ID_VIPR1 = "vipr1";
    public static final String NODE_ID_VIPR2 = "vipr2";
    public static final String NODE_ID_VIPR3 = "vipr3";
    public static final String NODE_ID_VIPR4 = "vipr4";
    public static final String NODE_ID_VIPR5 = "vipr5";
    public static final String NODE_COUNT_1_STRING = "1+0 (1 Server)";
    public static final String NODE_COUNT_3_STRING = "2+1 (3 Servers)";
    public static final String NODE_COUNT_5_STRING = "3+2 (5 Servers)";

    // Network Interface configuration labels
    public static final String NETWOK_INT_PANEL_TITLE = "Network Interface Configuration";
    public static final String NETWORK_INT_PANEL_INST = "Use ARROW UP/DOWN to view list, TAB/ENTER to move/select.";
    public static final String NETWORK_INT_PANEL_ID = "NETWORK_INT_PANEL_ID";
    public static final String NETWORK_INT_LABEL = "Available interface(s)";
    public static final String NETWORK_INT_CONFIG_WARN_MSG = "Please select a network interface";

    // Network configuration labels
    public static final String NETWORK_PANEL_INST = "Use TAB/ENTER key to select and enter network configuration";
    public static final String NETWORK_PANEL_TITLE = "Network Configuration";
    public static final String NETWORK_PANEL_ID = "NETWORK_PANEL_ID";
    public static final String IPV4_PANEL_TITLE = "IPv4 Configuration";
    public static final String IPV6_PANEL_TITLE = "IPv6 Configuration";
    public static final String IPV4_PANEL_INSTR = "(The value 0.0.0.0 disables IPv4.)";
    public static final String IPV6_PANEL_INSTR = "(The value ::0 disables IPv6.)";

    // property display names/labels
    public static final String DISPLAY_LABEL_NODE_ID = "Node Id: ";
    public static final String DISPLAY_LABEL_NODE_COUNT = "Node Count: ";
    public static final String DISPLAY_LABEL_DISK = "Disk: ";
    public static final String DISPLAY_LABEL_NETIF = "Network I/F: ";
    public static final String DISPLAY_LABEL_IPV4_NODE_ADDR = "Node %s: ";
    public static final String DISPLAY_LABEL_IPV4_VIP = "VIP: ";
    public static final String DISPLAY_LABEL_IPV4_NETMASK = "Netmask: ";
    public static final String DISPLAY_LABEL_IPV4_GATEWAY = "Gateway: ";
    public static final String DISPLAY_LABEL_IPV6_NODE_ADDR = "Node %s: ";
    public static final String DISPLAY_LABEL_IPV6_VIP = "VIP: ";
    public static final String DISPLAY_LABEL_IPV6_PREFIX = "Prefix: ";
    public static final String DISPLAY_LABEL_IPV6_GATEWAY = "Gateway: ";

    // Disk configuration labels
    public static final String DISK_PANEL_TITLE = "Disk Configuration";
    public static final String DISK_PANEL_INST = "Use ARROW UP/DOWN to view list, TAB/ENTER to move/select.";
    public static final String DISK_PANEL_ID = "DISK_PANEL_ID";
    public static final String DISK_CONFIG_LABEL = "Available disk(s)";
    public static final String DISK_CONFIG_WARN_MSG = "Please select a disk from the list";

    // Confirmation panel labels
    public static final String SUMMARY_PROPERTY_LABEL = "Configuration Properties";
    public static final String SUMMARY_PANEL_TITLE = "Deployment Confirmation";
    public static final String SUMMARY_PANEL_INST = "All data on the disk will be erased when installation starts.";
    public static final String SUMMARY_PANEL_ID = "SUMMARY_PANEL_ID";
    public static final String INSTALL_INST_1 = "Click Install button to confirm and start installation.";
    public static final String INSTALL_INST_2 = "All data on the disk will be erased when installation starts.";
    public static final String INITIAL_CONFIG_INST = "Click Config button to confirm and start configuration.";
    public static final String REDEPLOY_INST_1 = "Click Deploy button to confirm and start deployment.";
    public static final String REDEPLOY_INST_2 = "All data on the disk will be erased when deployment starts.";
    public static final String RECONFIG_INST_1 = "Click Config button to confirm and start reconfiguration.";
    public static final String RECONFIG_INST_2 = "Note: Please reconfig in all nodes even if one IP is changed.";

    // Configuration file location, name
    public static final String CONFIG_DIR = "/data/install/";
    public static final String CONFIG_FILE_PREFIX = "configfile";
    public static final String CONFIG_FILE_PATH = CONFIG_DIR + CONFIG_FILE_PREFIX;

    // Multicast Timeout
    public static final long NORMAL_MULTICAST_TIMEOUT = 7 * 24 * 3600 * 1000; // 1 week

    // startup mode file on disk
    public static final String STARTUPMODE = "startupmode";
    public static final String STARTUPMODE_HIBERNATE = "hibernate";
    public static final String DB_DIR = "/data/db";
    public static final String GEODB_DIR = "/data/geodb";
}
