/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block.tasks;

import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionTask;
import com.google.common.collect.Lists;
import com.iwave.ext.vmware.HostStorageAPI;
import com.vmware.vim25.mo.HostSystem;

public class RefreshStorage extends ExecutionTask<Void> {
    private final Collection<HostSystem> hosts;

    public RefreshStorage(Collection<HostSystem> hosts) {
        this.hosts = hosts;
        List<String> names = Lists.newArrayList();
        for (HostSystem host : hosts) {
            names.add(host.getName());
        }
        provideDetailArgs(StringUtils.join(names, ", "));
    }

    @Override
    public void execute() throws Exception {
        debug("Executing: %s", getDetail());
        for (HostSystem host : hosts) {
            try {
                HostStorageAPI storageAPI = new HostStorageAPI(host);
                storageAPI.refreshStorage();
            } catch (Exception e) {
                logWarn("RefreshStorage.detail.error", host.getName());
            }
        }
    }
}
