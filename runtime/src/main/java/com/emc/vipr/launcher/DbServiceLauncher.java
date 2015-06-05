/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.vipr.launcher;

public class DbServiceLauncher extends AbstractServiceLauncher {
    public DbServiceLauncher() {
        super("dbsvc");
    }

    @Override
    protected void runService() throws Exception {
        startBean(serviceName);
    }
}
