/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.server.impl;

import com.emc.storageos.db.server.DbService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import com.sun.jersey.api.json.JSONConfiguration;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.context.support.GenericXmlApplicationContext;

import java.io.IOException;

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

            DbService dbsvc = (DbService)ctx.getBean(SERVICE_BEAN);
            addShutdownHook(dbsvc);
            dbsvc.start();
        } catch(Exception e) {
            _log.error("Failed to start {}:", SERVICE_BEAN, e);
            System.exit(1);
        } 
    }

    private static void addShutdownHook(final DbService dbsvc) {
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() {
                _log.info("Shutting down {}", SERVICE_BEAN);
                try {
                    dbsvc.stopWithDecommission();
                } catch(Exception e) {
                     _log.error("Failed to stop {}:", SERVICE_BEAN, e);
                }
            }
        });
    }
}
