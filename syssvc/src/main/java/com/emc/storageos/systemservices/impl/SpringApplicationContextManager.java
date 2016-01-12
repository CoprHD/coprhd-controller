/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl;

import org.springframework.context.ApplicationContext;

public class SpringApplicationContextManager {

    private static ApplicationContext appCtx;

    public static void setApplicationContext(ApplicationContext ctx) {
        appCtx = ctx;
    }

    public static ApplicationContext getApplicationContext() {
        return appCtx;
    }
}
