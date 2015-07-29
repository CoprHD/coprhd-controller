/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl;

import com.emc.storageos.api.service.ProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.context.support.GenericXmlApplicationContext;

/**
 * Use this to start provisioning service with a spring config
 */
public class Main {
    private static final String SERVICE_BEAN = "apiservice";
    private static final Logger _log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            SLF4JBridgeHandler.install();
            // To using Spring profile feature
            GenericXmlApplicationContext ctx = new GenericXmlApplicationContext();
            ctx.getEnvironment().setActiveProfiles(System.getProperty("buildType"));
            ctx.load(args);
            ctx.refresh();

            ProvisioningService apiservice = (ProvisioningService) ctx.getBean(SERVICE_BEAN);
            apiservice.start();
        } catch (Exception e) {
            _log.error("failed to start {}:", SERVICE_BEAN, e);
            System.exit(1);
        }
    }
}
