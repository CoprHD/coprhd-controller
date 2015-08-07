/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.context.support.GenericXmlApplicationContext;

/**
 * Use this to start authentication service with a spring config
 */
public class Main {
    private static final String SERVICE_BEAN = "authnserver";
    private static final Logger _log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            SLF4JBridgeHandler.install();
            // To using Spring profile feature
            GenericXmlApplicationContext ctx = new GenericXmlApplicationContext();
            ctx.getEnvironment().setActiveProfiles(System.getProperty("buildType"));
            ctx.load(args);
            ctx.refresh();

            AuthenticationServerImpl service = (AuthenticationServerImpl) ctx.getBean(SERVICE_BEAN);
            service.start();
        } catch (Exception e) {
            _log.error("failed to start {}:", SERVICE_BEAN, e);
            System.exit(1);
        }
    }
}
