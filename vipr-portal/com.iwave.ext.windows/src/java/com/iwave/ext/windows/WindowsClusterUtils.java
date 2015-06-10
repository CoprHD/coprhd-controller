/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows;

import com.google.common.collect.Lists;
import com.iwave.ext.windows.model.wmi.MSClusterNetworkInterface;
import com.iwave.ext.windows.model.wmi.Win32Service;
import com.iwave.ext.windows.winrm.WinRMSoapException;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

/**
 */
public class WindowsClusterUtils {
    private static final String CLUSTER_SERVICE_NAME  = "clussvc";

    public static Win32Service findClusterService(List<Win32Service> services) {
        for (Win32Service service : services) {
            if (service.getName().equalsIgnoreCase(CLUSTER_SERVICE_NAME)) {
                return service;
            }
        }

        return null;
    }

    public static List<String> getClusterIpAddresses(List<MSClusterNetworkInterface> networkInterfaces) {
        List<String> ipAddresses = Lists.newArrayList();

        for (MSClusterNetworkInterface networkInterface : networkInterfaces) {
            if (StringUtils.isNotBlank(networkInterface.getIpaddress())) {
                ipAddresses.add(networkInterface.getIpaddress());
            }
        }

        return ipAddresses;
    }

    public static String findWindowsClusterHostIsIn(String hostName, Map<String, List<MSClusterNetworkInterface>> clusters) {
        String hostAddress = null;
        try {
            InetAddress address = Inet4Address.getByName(hostName);
            hostAddress = address.getHostAddress();
        }
        catch (UnknownHostException e) {
            throw new RuntimeException("Unable to resolve hostname "+hostName,e);
        }


        for (Map.Entry<String, List<MSClusterNetworkInterface>> entry : clusters.entrySet()) {
            for (MSClusterNetworkInterface networkInterface : entry.getValue()) {
                if (hostAddress.equals(networkInterface.getIpaddress())) {
                    return entry.getKey();
                }
            }
        }

        return null;
    }
}
