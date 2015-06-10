/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import java.util.Collection;
import java.util.Map;

import com.google.common.collect.Maps;
import com.iwave.ext.windows.model.wmi.FibreChannelHBA;

public class FindFibreChannelHBAs extends WindowsExecutionTask<Map<String, FibreChannelHBA>> {
    private Collection<String> portWWNs;

    public FindFibreChannelHBAs(Collection<String> portWWNs) {
        this.portWWNs = portWWNs;
        provideDetailArgs(portWWNs);
    }

    @Override
    public Map<String, FibreChannelHBA> executeTask() throws Exception {
        Map<String, FibreChannelHBA> results = Maps.newHashMap();
        for (FibreChannelHBA hba : getTargetSystem().listFibreChannelHBAs()) {
            if (portWWNs.contains(hba.getPortWWN())) {
                results.put(hba.getPortWWN(), hba);
            }
        }
        if (results.size() < portWWNs.size()) {
            throw stateException("illegalState.FindFibreChannelHBAs.noHBAs", portWWNs, results.values());
        }
        return results;
    }
}
