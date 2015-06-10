/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.launcher;

public class AuthServiceLauncher extends AbstractServiceLauncher {
    public AuthServiceLauncher() {
        super("auth");
    }

    @Override
    protected void runService() throws Exception {
        startBean("authnserver");
    }
}
