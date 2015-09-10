/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.iwave.ext.windows.model.wmi.FibreChannelHBA;
import com.iwave.ext.windows.model.wmi.FibreChannelTargetMapping;

public class FindFibreChannelTargetMappings extends
        WindowsExecutionTask<Map<String, List<FibreChannelTargetMapping>>> {
    private final String instanceName;
    private final String portWWN;
    private final Collection<String> targetWWNs;

    public FindFibreChannelTargetMappings(FibreChannelHBA hba, Collection<String> targetWWNs) {
        this(hba.getInstanceName(), hba.getPortWWN(), targetWWNs);
    }

    public FindFibreChannelTargetMappings(String instanceName, String portWWN,
            Collection<String> targetWWNs) {
        this.instanceName = instanceName;
        this.portWWN = portWWN;
        this.targetWWNs = targetWWNs;
        provideDetailArgs(instanceName, portWWN, targetWWNs);
    }

    @Override
    public Map<String, List<FibreChannelTargetMapping>> executeTask() throws Exception {
        Map<String, List<FibreChannelTargetMapping>> results = Maps.newHashMap();
        for (FibreChannelTargetMapping mapping : getTargetSystem().getTargetMapping(instanceName,
                portWWN)) {
            if (targetWWNs.contains(mapping.getPortWWN())) {
                List<FibreChannelTargetMapping> mappings = results.get(mapping.getPortWWN());
                if (mappings == null) {
                    mappings = Lists.newArrayList();
                    results.put(mapping.getPortWWN(), mappings);
                }
                mappings.add(mapping);
            }
        }
        return results;
    }
}
