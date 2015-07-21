/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.launcher;

public class CoordinatorServiceLauncher extends AbstractServiceLauncher {
    public CoordinatorServiceLauncher() {
        super("coordinatorsvc");
    }

    @Override
    protected void runService() throws Exception {
        startBean(serviceName);
    }
}
