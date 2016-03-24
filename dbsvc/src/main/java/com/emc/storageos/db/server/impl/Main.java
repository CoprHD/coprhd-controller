/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.impl;

import com.emc.storageos.db.server.DbService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.context.support.GenericXmlApplicationContext;

/**
 * Use this to start a db service using spring config
 */
public class Main {
    private static final String SERVICE_BEAN = "dbsvc";
    private static final Logger _log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            SLF4JBridgeHandler.install();
            // To using Spring profile feature
            GenericXmlApplicationContext ctx = new GenericXmlApplicationContext();
            ctx.getEnvironment().setActiveProfiles(System.getProperty("buildType"));
            ctx.load(args);
            ctx.refresh();

            DbService dbsvc = (DbService) ctx.getBean(SERVICE_BEAN);
            addShutdownHook(dbsvc);
            dbsvc.start();
        } catch (Exception e) {
            _log.error("Failed to start {}:", SERVICE_BEAN, e);
            System.exit(1);
        }
    }

    private static void addShutdownHook(final DbService dbsvc) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                _log.info("Shutting down {}", SERVICE_BEAN);
                try {
                    dbsvc.stop();
                } catch (Exception e) {
                    _log.error("Failed to stop {}:", SERVICE_BEAN, e);
                }
            }
        });
    }
}
