/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import com.iwave.ext.windows.WindowsClusterUtils;
import com.iwave.ext.windows.model.wmi.Win32Service;

import java.util.List;

public class VerifyFailoverClusterInstalled extends WindowsExecutionTask<Void> {

    @Override
    public void execute() throws Exception {
        String url = getTargetSystem().getTarget().getUrl().toExternalForm();
        provideDetailArgs(url);

        List<Win32Service> services =getTargetSystem().listServices();

        Win32Service clusterService = WindowsClusterUtils.findClusterService(services);
        if (clusterService != null && !clusterService.isStarted()) {
            throw stateException("illegalState.VerifyFailoverClusterInstalled.notStarted", getTargetSystem().getTarget().getHost());
        }

        if (clusterService == null) {
            throw stateException("illegalState.VerifyFailoverClusterInstalled.notInstalled", getTargetSystem().getTarget().getHost());
        }
    }
}
