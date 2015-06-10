/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.launcher;

public class GeoDbServiceLauncher extends AbstractServiceLauncher {
    public GeoDbServiceLauncher() {
        super("geodbsvc");
    }

    @Override
    protected void runService() throws Exception {
        startBean("dbsvc");
    }
}
