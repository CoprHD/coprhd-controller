/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.launcher;

public class ControllerServiceLauncher extends AbstractServiceLauncher {
    public ControllerServiceLauncher() {
        super("controllersvc");
    }

    @Override
    protected void runService() throws Exception {
        startBean(serviceName);
    }
}
