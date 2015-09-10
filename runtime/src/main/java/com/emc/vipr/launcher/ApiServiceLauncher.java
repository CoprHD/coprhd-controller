/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.launcher;

public class ApiServiceLauncher extends AbstractServiceLauncher {
    public ApiServiceLauncher() {
        super("apisvc");
    }

    @Override
    protected void runService() throws Exception {
        startBean("apiservice");
    }
}
