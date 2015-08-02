/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import java.util.Map;

public class GetDiskToResourceMap extends WindowsExecutionTask<Map<String, String>> {

    @Override
    public Map<String, String> executeTask() throws Exception {
        String url = getTargetSystem().getTarget().getUrl().toExternalForm();
        provideDetailArgs(url);

        return getTargetSystem().getDiskToClusterResourceMap();
    }

}
