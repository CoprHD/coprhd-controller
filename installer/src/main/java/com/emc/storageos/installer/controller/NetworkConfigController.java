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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import charvax.swing.JTextField;

import com.emc.storageos.installer.util.InstallerUtil;
import com.emc.storageos.services.util.Configuration;
import com.emc.storageos.installer.widget.TextInputPanel;

/**
 * Class implements the control for Network configuration page.
 *
 */
public class NetworkConfigController implements IConfigPanelController {
    private static final Logger log = LoggerFactory.getLogger(NetworkConfigController.class);
    private Configuration config;
    private TextInputPanel ipv4Panel;
    private TextInputPanel ipv6Panel;

    public NetworkConfigController(Configuration config, TextInputPanel ipv4Panel, TextInputPanel ipv6Panel) {
        this.config = config;
        this.ipv4Panel = ipv4Panel;
        this.ipv6Panel = ipv6Panel;
    }

    /*
     * Validate the network settings for duplicate, invalid format and if they are on the same network
     * 
     * @param ipv4Map the Ipv4 network settings
     * 
     * @param ipv6Map the Ipv6 network settings
     * 
     * @return the list of parameters not pass validation
     */
    private List<String> validateNetworkSettings(LinkedHashMap<String, String> ipv4Map, LinkedHashMap<String, String> ipv6Map) {
        // validate entered network setting before save it
        List<String> totalErrList = new ArrayList<String>();
        // 1. check if both ipv4 and ipv6 addresses are default/disabled
        // user has to set either ipv4 or ipv6, or both
        if (InstallerUtil.ipAddressNotConfigured(ipv4Map, ipv6Map)) {
            totalErrList.add("Both IPv4 and IPv6 networks are not configured.");
            totalErrList.add("At least one (IPv4 or IPv6) network has to be configured.");
            return totalErrList;
        }
        // 2. check if there are duplicate
        List<String> dupList = InstallerUtil.checkDuplicateAddress(ipv4Map, ipv6Map);
        if (dupList != null && !dupList.isEmpty()) {
            totalErrList.add("Entries are duplicate:");
            totalErrList.addAll(dupList);
            return totalErrList;
        }
        // 3. check IPv4 and IPv6 address format
        List<String> invalidList = InstallerUtil.checkInvalidAddress(ipv4Map, ipv6Map);
        if (invalidList != null && !invalidList.isEmpty()) {
            if (!totalErrList.isEmpty()) {
                totalErrList.add("");
            }
            totalErrList.add("Entrie(s) with invalid IP address format:");
            totalErrList.addAll(invalidList);
            return totalErrList;
        }

        // 4. check if addresses on the same sub network
        List<String> notSameNetList = InstallerUtil.checkAddressesOnSameSubNetwork(ipv4Map, ipv6Map);
        if (notSameNetList != null && !notSameNetList.isEmpty()) {
            if (!totalErrList.isEmpty()) {
                totalErrList.add("");
            }
            totalErrList.add("Entries are not on same sub network:");
            totalErrList.addAll(notSameNetList);
        }
        return totalErrList;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.installer.controller.IConfigPanelController#configurationIsCompleted()
     */
    @Override
    public String[] configurationIsCompleted() {
        String[] err = null;
        LinkedHashMap<String, String> ipv4Map = new LinkedHashMap<String, String>();
        Iterator<Entry<String, JTextField>> ite = ipv4Panel.getFieldMap().entrySet().iterator();
        while (ite.hasNext()) {
            Entry<String, JTextField> entry = ite.next();
            ipv4Map.put(entry.getKey(), entry.getValue().getText().trim());
        }

        LinkedHashMap<String, String> ipv6Map = new LinkedHashMap<String, String>();
        ite = ipv6Panel.getFieldMap().entrySet().iterator();
        while (ite.hasNext()) {
            Entry<String, JTextField> entry = ite.next();
            ipv6Map.put(entry.getKey(), entry.getValue().getText().trim());
        }

        // validate entered network setting before save it
        List<String> totalErrList = validateNetworkSettings(ipv4Map, ipv6Map);
        if (totalErrList != null && !totalErrList.isEmpty()) {
            log.warn("Invalid network parameter entered: {}", totalErrList);
            err = totalErrList.toArray(new String[totalErrList.size()]);
        } else {
            config.setIpv4NetworkConfig(InstallerUtil.convertIpv4DisplayNameToPropertyKey(ipv4Map));
            config.setIpv6NetworkConfig(InstallerUtil.convertIpv6DisplayNameToPropertyKey(ipv6Map));
        }
        return err;
    }

}
