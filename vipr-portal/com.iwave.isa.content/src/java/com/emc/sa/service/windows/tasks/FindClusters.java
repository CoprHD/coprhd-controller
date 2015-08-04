/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import com.iwave.ext.windows.model.wmi.MSCluster;
import java.util.List;

/**
 */
public class FindClusters extends WindowsExecutionTask<List<MSCluster>> {

    @Override
    public List<MSCluster> executeTask() throws Exception {
        return getTargetSystem().listClusters();
    }
}
