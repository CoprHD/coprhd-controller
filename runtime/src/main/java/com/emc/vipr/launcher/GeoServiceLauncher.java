/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.vipr.launcher;

public class GeoServiceLauncher extends AbstractServiceLauncher {
    public GeoServiceLauncher() {
        super("geosvc");
    }

    @Override
    protected void runService() throws Exception {
        startBean("geoservice");
    }
}
