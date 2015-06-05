/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.model.Host;
import com.google.common.collect.Lists;
import com.iwave.ext.windows.model.wmi.MSClusterNetworkInterface;

public class VerifyClusterConfiguration extends WindowsExecutionTask<Void> {
    private List<Host> hosts;

    public VerifyClusterConfiguration(List<Host> hosts) {
        this.hosts = hosts;
        provideDetailArgs(getHostsDisplay(hosts));
    }

    @Override
    public void execute() throws Exception {
        Map<String, List<MSClusterNetworkInterface>> clusterInterfaces = getTargetSystem().getClusterToNetworkInterfaces();

        List<HostWithAddress> hostAddresses = getHostAddresses();
        List<MSClusterNetworkInterface> networkInterfaces = findClusterInterfaces(clusterInterfaces, hostAddresses);

        if (networkInterfaces == null) {
            return;
        }

        // Find Hosts in the MS Cluster that aren't in ViPR Cluster
        List<MSClusterNetworkInterface> notInViPRHosts = Lists.newArrayList();
        for (MSClusterNetworkInterface networkInterface : networkInterfaces) {
            if (!hostsContainsClusterInterface(hostAddresses, networkInterface)) {
                notInViPRHosts.add(networkInterface);
            }
        }

        // Find Hosts that are in ViPR Cluster but not in MS Cluster
        List<HostWithAddress> notInClusterHosts = Lists.newArrayList();
        for (HostWithAddress host : hostAddresses) {
            if (!clusterInterfacesContainsHost(networkInterfaces, host)) {
                notInClusterHosts.add(host);
            }
        }

        if (!notInClusterHosts.isEmpty() || !notInViPRHosts.isEmpty()) {
            StringBuffer notInViPRHostsMsg = new StringBuffer();
            if (!notInViPRHosts.isEmpty()) {
                for (MSClusterNetworkInterface missingHost: notInViPRHosts) {
                    notInViPRHostsMsg.append(String.format("%s [%s],",missingHost.getNode(),missingHost.getIpaddress()));
                }
            }

            StringBuffer notInClusterHostsMsg = new StringBuffer();
            if (!notInClusterHosts.isEmpty()) {
                for (HostWithAddress extraHost: notInClusterHosts) {
                    notInClusterHostsMsg.append(String.format("%s [%s],",extraHost.getHost().getLabel(), extraHost.getHost().getHostName()));
                }
            }

            logWarn("verify.cluster.conf.inconsistent", notInViPRHostsMsg, notInClusterHostsMsg);
        }
    }

    /* Returns the first cluster that contains one of the hosts.
    * There should really only be one cluster, but there COULD be more. Since we don't know the name of the cluster, this is the only way
    * we can find a matching cluster.
    */
    private List<MSClusterNetworkInterface> findClusterInterfaces(Map<String, List<MSClusterNetworkInterface>> networkInterfaces, List<HostWithAddress> hostAddresses) {
        for (Map.Entry<String, List<MSClusterNetworkInterface>> entry : networkInterfaces.entrySet()) {
            for (HostWithAddress host : hostAddresses) {
                if (clusterInterfacesContainsHost(entry.getValue(), host)) {
                    logDebug("verify.cluster.conf.ms"+entry.getKey());
                    setDetail(getClusterDisplay(entry.getKey()));
                    return entry.getValue();
                }
            }
        }

        logWarn("verify.cluster.conf.cluster.not.found");
        return null;
    }

    private boolean clusterInterfacesContainsHost(List<MSClusterNetworkInterface> networkInterfaces, HostWithAddress host) {
        for (MSClusterNetworkInterface networkInterface : networkInterfaces) {
            if (networkInterface.getIpaddress().equals(host.getIpAddress())) {
                return true;
            }
        }

        return false;
    }

    private boolean hostsContainsClusterInterface(List<HostWithAddress> hostWithAddresses, MSClusterNetworkInterface networkInterface ) {
        if (networkInterface != null && networkInterface.getIpaddress() != null) {
            for (HostWithAddress host : hostWithAddresses) {
                if (StringUtils.equals(host.getIpAddress(), networkInterface.getIpaddress())) {
                    return true;
                }
            }
        }

        return false;
    }

    private List<HostWithAddress> getHostAddresses() throws Exception {
        List<HostWithAddress> hostsWithAddresses = Lists.newArrayList();

        for (Host host : hosts) {
            hostsWithAddresses.add(new HostWithAddress(host));
        }

        return hostsWithAddresses;
    }

    private static class HostWithAddress {
        private Host host;
        private String ipAddress;

        public HostWithAddress(Host host) throws Exception {
            this.host = host;

            InetAddress address = Inet4Address.getByName(host.getHostName());
            if (!address.getHostAddress().equals(host.getHostName())) {
                ipAddress = address.getHostAddress();
            }
        }

        public Host getHost() {
            return host;
        }

        public String getIpAddress() {
            return ipAddress;
        }
    }
    
    public static String getHostsDisplay(List<Host> hosts) {
        StringBuilder sb = new StringBuilder();
        Iterator<Host> i = hosts.iterator();
        while(i.hasNext()) {
            Host host = i.next();
            sb.append(host.getId());
            if (i.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }
    
    private String getClusterDisplay(String clusterName) {
        return getMessage("VerifyClusterConfiguration.clusterDisplay", clusterName);
    }
}
