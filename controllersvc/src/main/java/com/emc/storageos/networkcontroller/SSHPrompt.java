/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller;

import com.emc.storageos.imageservercontroller.impl.ImageServerDialogProperties;
import com.emc.storageos.networkcontroller.impl.mds.MDSDialogProperties;

public enum SSHPrompt {

    LINUX_CUSTOM_PROMPT(ImageServerDialogProperties.getString("SSHPrompt.LINUX_CUSTOM_PROMPT")),

    // <<devname>> represents the device name and will be substituted before matching.
    NOMATCH(""),
    POUND(MDSDialogProperties.getString("SSHPrompt.POUND")), // \#\\s*
    GREATER_THAN(MDSDialogProperties.getString("SSHPrompt.GREATER_THAN")), // >\\s*

    // MDS prompts
    MDS_POUND(MDSDialogProperties.getString("SSHPrompt.MDS_POUND")),  // <<devname>>\#\\s*
    MDS_GREATER_THAN(MDSDialogProperties.getString("SSHPrompt.MDS_GREATER_THAN")), // <<devname>>>\\s*
    MDS_ENABLE(MDSDialogProperties.getString("SSHPrompt.MDS_ENABLE")), // ^[^\\[]\\S+\#(?\!\#) +
    MDS_CONFIG(MDSDialogProperties.getString("SSHPrompt.MDS_CONFIG")), // <<devname>>>\\s*
    MDS_CONFIG_ZONE(MDSDialogProperties.getString("SSHPrompt.MDS_CONFIG_ZONE")), // <<devname>>\\s*\\(config-zone\\)\\s*\# +
    MDS_CONFIG_ZONESET(MDSDialogProperties.getString("SSHPrompt.MDS_CONFIG_ZONESET")), // <<devname>>\\s*\\(config-zoneset\\)\\s*\# +
    MDS_CONFIG_IVR_ZONE(MDSDialogProperties.getString("SSHPrompt.MDS_CONFIG_IVR_ZONE")), // <<devname>>\\s*\\(config-zone\\)\\s*\# +
    MDS_CONFIG_IVR_ZONESET(MDSDialogProperties.getString("SSHPrompt.MDS_CONFIG_IVR_ZONESET")), // <<devname>>\\s*\\(config-zoneset\\)\\s*\#
                                                                                               // +
    MDS_NESTED_CONFIG(MDSDialogProperties.getString("SSHPrompt.MDS_NESTED_CONFIG")), // [-0-9A-Za-z_]+\\s*\\(config-\\S+\\)\\s*\#
    MDS_CONTINUE_QUERY(MDSDialogProperties.getString("SSHPrompt.MDS_CONTINUE_QUERY")),  // Do you want to continue? (y/n)
    MDS_CONFIG_DEVICE_ALIAS(MDSDialogProperties.getString("SSHPrompt.MDS_CONFIG_DEVICE_ALIAS"));   // <<devname>>\\s*\\(config-device-alias-db\\)\\s*\#
                                                                                                 // +

    private String regex;

    private SSHPrompt(String regex) {
        this.regex = regex;
    }

    public String getRegex() {
        return regex;
    }

}
