/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import java.util.List;

import org.apache.commons.lang.StringUtils;

public abstract class DiskPartScript<T> extends WindowsExecutionTask<T> {
    public void setDiskPartCommands(List<String> commands) {
        provideDetailArgs(StringUtils.join(commands, " ; "));
    }
}
