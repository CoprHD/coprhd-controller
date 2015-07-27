/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.file.tasks;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.google.common.collect.Sets;
import com.vmware.vim25.HostVirtualNic;
import com.vmware.vim25.mo.ClusterComputeResource;
import com.vmware.vim25.mo.HostSystem;

/**
 * Gets all host endpoints for the host or cluster.
 * 
 * @author jonnymiller
 */
public class GetEndpoints extends ViPRExecutionTask<Set<String>> {
    private HostSystem host;
    private ClusterComputeResource cluster;

    public GetEndpoints(HostSystem host, ClusterComputeResource cluster) {
        this.host = host;
        this.cluster = cluster;
        if (cluster != null) {
            provideDetailArgs(getMessage("GetEndpoints.detail.cluster"), cluster.getName());
        }
        else {
        	provideDetailArgs(getMessage("GetEndpoints.detail.host"), host.getName());
        }
    }

    @Override
    public Set<String> executeTask() throws Exception {
        Set<String> endpoints = Sets.newHashSet();
        for (HostSystem host : getHosts()) {
            Set<String> ipAddresses = getIpAddresses(host);
            if (ipAddresses.isEmpty()) {
                throw stateException("GetEndpoints.illegalState.ipNotInHost", host.getName());
            }
            logInfo("endpoints.using.all", ipAddresses, host.getName());
            endpoints.addAll(ipAddresses);
        }
        return endpoints;
    }

    private List<HostSystem> getHosts() {
        if (cluster != null) {
            if (cluster.getHosts() != null) {
                return Arrays.asList(cluster.getHosts());
            }
            return Collections.emptyList();
        }
        return Arrays.asList(host);
    }

    private Set<String> getIpAddresses(HostSystem host) {
        Set<String> ipAddresses = Sets.newHashSet();
        if ((host.getConfig() != null) && (host.getConfig().getNetwork() != null)
                && (host.getConfig().getNetwork().getVnic() != null)) {
            for (HostVirtualNic nic : host.getConfig().getNetwork().getVnic()) {
                String ipAddress = nic.getSpec().getIp().getIpAddress();
                if (StringUtils.isNotBlank(ipAddress)) {
                    ipAddresses.add(ipAddress);
                }
            }
        }
        return ipAddresses;
    }
}
