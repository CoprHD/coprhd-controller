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
package com.emc.storageos.simulators.impl;

import java.util.Map;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.emc.storageos.simulators.StorageCtlrSimulator;
import com.emc.storageos.simulators.db.DbSvcBase;
import com.emc.storageos.simulators.eventmanager.EventManager;
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;

/*
 * Storage controller simulator default implementation
 */
public class StorageCtlrSimulatorImpl implements StorageCtlrSimulator {
    private Server _server;
    private Application _app;
    private int _port;
    private DbSvcBase _dbsvc;

    public void setPort(int port) {
        _port = port;
    }

    public void setApplication(Application app) {
        _app = app;
    }

    public void setDbsvc(DbSvcBase dbsvc) {
        _dbsvc = dbsvc;
    }

    @Override
    public void start() throws Exception {
        _server = new Server(_port);
        ServletContextHandler rootHandler = new ServletContextHandler(_server, "/", ServletContextHandler.NO_SESSIONS);
        if (_dbsvc != null) {
            _dbsvc.startDb();
        }
        if (_app != null) {
            ResourceConfig config = new DefaultResourceConfig();
            config.add(_app);
            Map<String, MediaType> type = config.getMediaTypeMappings();
            type.put("json", MediaType.APPLICATION_JSON_TYPE);
            rootHandler.addServlet(new ServletHolder(new ServletContainer(config)), "/*");
            _server.start();
        } else {
            throw new Exception("No app found.");
        }
        EventManager.getInstance().start();
    }

    @Override
    public void stop() throws Exception {
        _server.stop();
    }

}
