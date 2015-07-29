/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.launcher;

public class SaServiceLauncher extends AbstractServiceLauncher {

    public SaServiceLauncher() {
        super("sasvc");
    }

    @Override
    protected void runService() throws Exception {
        startBean("saservice");
    }
}
