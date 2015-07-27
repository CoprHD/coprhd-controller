/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.management.backup;

import com.emc.storageos.services.util.JmxServerWrapper;

import org.apache.cassandra.service.CassandraDaemon;

/**
 * Db Simulator
 */
public class DbSimulator {
    private JmxServerWrapper jmxServer;
    private CassandraDaemon service;
    private String config;

    /**
     * JMX server wrapper
     */
    public void setJmxServer(final JmxServerWrapper jmxServer) {
        this.jmxServer = jmxServer;
    }

    /**
     * Gets JMX server
     */
    public JmxServerWrapper getJmxServer() {
        return this.jmxServer;
    }

    /**
     * Set database config file.  It must be in URI form or file must be
     * be in classpath
     *
     * @param config database config file
     */
    public void setConfig(String config) {
        this.config = config;
    }

    /**
     * Starts Cassandra and JMX service
     * @throws Exception
     */
    public void start() throws Exception {
        System.setProperty("cassandra.config", config);

        if( jmxServer != null)
            jmxServer.start();

        service = new CassandraDaemon();
        service.init(null);
        service.start();
    }

    /**
     * Stops Cassandra and JMX service
     */
    public void stop() {
        if (jmxServer != null)
           jmxServer.stop();

        service.stop();
    }
} 
