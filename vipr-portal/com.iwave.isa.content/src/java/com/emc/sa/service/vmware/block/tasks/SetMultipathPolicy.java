/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block.tasks;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionTask;
import com.google.common.collect.Lists;
import com.iwave.ext.vmware.HostStorageAPI;
import com.vmware.vim25.HostScsiDisk;
import com.vmware.vim25.mo.HostSystem;

public class SetMultipathPolicy extends ExecutionTask<Void> {
    private Map<HostSystem, HostScsiDisk> hostDisks;
    private String multipathPolicy;

    public SetMultipathPolicy(Map<HostSystem, HostScsiDisk> hostDisks, String multipathPolicy) {
        this.hostDisks = hostDisks;
        this.multipathPolicy = multipathPolicy;
        List<String> names = Lists.newArrayList();
        for (HostSystem host : hostDisks.keySet()) {
            names.add(host.getName());
        }
        provideDetailArgs(multipathPolicy, StringUtils.join(names, ", "));
    }

    @Override
    public void execute() throws Exception {
        debug("Executing: %s", getDetail());
        for (HostSystem host : hostDisks.keySet()) {
            try {
                HostStorageAPI storageAPI = new HostStorageAPI(host);
                storageAPI.setMultipathPolicy(hostDisks.get(host), multipathPolicy);
            } catch (Exception e) {
                logWarn("vmware.support.multipath.policy.failed", multipathPolicy, hostDisks.get(host).getCanonicalName(), host.getName());
            }
        }
    }
}