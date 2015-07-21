/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.context.support.GenericXmlApplicationContext;

public class Main {
    private static final Logger _log = LoggerFactory.getLogger(Main.class);
    private static final String BUILD_TYPE = "buildType";
    private static final String SERVICE_BEAN = "syssvcserver";

    public static void main(String[] args) {
        try {
            SLF4JBridgeHandler.install();
            GenericXmlApplicationContext ctx = new GenericXmlApplicationContext();
            ctx.getEnvironment().setActiveProfiles(System.getProperty(BUILD_TYPE));
            ctx.load(args);
            ctx.refresh();
            SysSvcImpl sysservice = (SysSvcImpl) ctx.getBean(SERVICE_BEAN);
            sysservice.start();
        } catch (Exception e) {
            _log.error("failed to start {}:", SERVICE_BEAN, e);
            System.exit(1);            
        }
    }
}
