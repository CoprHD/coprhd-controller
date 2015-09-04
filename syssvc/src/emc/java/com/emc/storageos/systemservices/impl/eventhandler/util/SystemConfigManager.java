/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices.impl.eventhandler.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class SystemConfigManager {
    public static String getSoftwareVersion() {
        String version = "1.0.0.56";

        return version;
    }

    public static List<String> getNodeList() {
        List<String> nodelist = new ArrayList<String>();
        nodelist.add("Node1");
        nodelist.add("Node2");

        return nodelist;
    }

    public static List<String> getHotfixLIst() {
        List<String> hflist = new ArrayList<String>();
        hflist.add("HF-000123");
        hflist.add("HF-000234");

        return hflist;
    }

    public static String getHostname() {
        String hostname = null;
        try {
            InetAddress addr = InetAddress.getLocalHost();
            hostname = addr.getHostName();
            System.out.println("hostname=" + hostname);
        } catch (UnknownHostException e) {
        }
        return hostname;
    }
}
