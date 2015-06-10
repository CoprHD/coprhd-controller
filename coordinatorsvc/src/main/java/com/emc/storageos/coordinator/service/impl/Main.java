/*
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

package com.emc.storageos.coordinator.service.impl;

import com.emc.storageos.coordinator.service.Coordinator;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;


/**
 * Use this with spring config to start a coordinator node.  Or write your own main.
 */
public class Main {
    private static final String SERVICE_BEAN = "coordinatorsvc";
    private static final int MAX_ZK_BUFFER_SIZE = 4096 * 1024;
    private static final Logger _log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            FileSystemXmlApplicationContext ctx = new FileSystemXmlApplicationContext(args);
            Coordinator coordinator = (Coordinator)ctx.getBean(SERVICE_BEAN);
            addShutdownHook(coordinator);
            setZKOptions();
            coordinator.start();
        } catch(Exception e) {
            _log.error("Failed to start {}:", SERVICE_BEAN, e);
            System.exit(1);
        }
    }

    private static void addShutdownHook(final Coordinator coordinator) {
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() {
                _log.info("Shutting down {}", SERVICE_BEAN);
                try {
                    coordinator.stop();
                } catch (Exception e) {
                    _log.error("Failed to stop {}:", SERVICE_BEAN, e);
                }
            }
        });
    }

    private static void setZKOptions() {
        /**
         * It's a workaround for CTRL-10387. Because in rare instances we will hit this zookeeper packet
         * buffer limit(1M by default) when doing the upgrade, then it will cause client losing connection,
         * so we increase this buffer.
         * More information about this threshold, see http://zookeeper.apache.org/doc/r3.3.2/zookeeperAdmin.html
         */
        System.setProperty("jute.maxbuffer", String.valueOf(MAX_ZK_BUFFER_SIZE));
    }
}
