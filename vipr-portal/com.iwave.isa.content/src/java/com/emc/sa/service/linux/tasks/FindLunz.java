/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.iwave.ext.linux.command.ListLunInfoCommand;
import com.iwave.ext.linux.model.LunInfo;

public class FindLunz extends LinuxExecutionTask<List<LunInfo>> {
    @Override
    public List<LunInfo> executeTask() throws Exception {
        List<LunInfo> lunz = Lists.newArrayList();
        for (LunInfo lunInfo : executeCommand(new ListLunInfoCommand())) {
            if (StringUtils.equalsIgnoreCase("LUNZ", lunInfo.getModel())) {
                lunz.add(lunInfo);
            }
        }
        return lunz;
    }
}
