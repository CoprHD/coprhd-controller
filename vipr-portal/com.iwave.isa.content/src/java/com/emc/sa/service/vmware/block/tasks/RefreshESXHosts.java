/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block.tasks;

import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionTask;
import com.iwave.ext.vmware.HostStorageAPI;
import com.iwave.ext.vmware.VCenterAPI;
import com.vmware.vim25.mo.HostSystem;

public class RefreshESXHosts extends ExecutionTask<Void> {
    @Inject
    private VCenterAPI vcenter;
    private final String datacenter;
    private final List<String> hostNames;

    public RefreshESXHosts(String datacenter, List<String> hostNames) {
        this.datacenter = datacenter;
        this.hostNames = hostNames;
        provideDetailArgs(StringUtils.join(hostNames, ", "), datacenter);
    }

    @Override
    public void execute() throws Exception {
        for (String hostName : hostNames) {
            HostSystem host = vcenter.findHostSystem(datacenter, hostName);
            if (host == null) {
                throw stateException("RefreshESXHosts.illegalState.esxNotFound", hostName, datacenter);
            }
            debug("Refreshing storage on %s [%s]", hostName, datacenter);
            HostStorageAPI hostStorageAPI = new HostStorageAPI(host);
            hostStorageAPI.refreshStorage();
        }
    }
}
