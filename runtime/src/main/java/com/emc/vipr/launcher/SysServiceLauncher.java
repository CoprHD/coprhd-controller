/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.vipr.launcher;

public class SysServiceLauncher extends AbstractServiceLauncher {
    public SysServiceLauncher() {
        super("syssvc");
    }

    @Override
    protected void runService() throws Exception {
        startBean("syssvcserver");
    }
}
