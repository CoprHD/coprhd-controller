/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vasa.service.impl;

import com.emc.storageos.security.AbstractSecuredWebServer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * The implementation of embedded jetty for vasa
 */
public class VasaServer extends AbstractSecuredWebServer {

    // Context for Axis2 and vasaService
    private WebAppContext webAppContext;

    public synchronized void start() throws Exception {
        initServer();
        _server.start();
    }

    public synchronized void stop() throws Exception {
        _server.stop();
    }

    @Override
    /**
     * Override the method as Axis configuration is needed
     * @throws Exception
     */
    public void initServer() throws Exception {

        _server = new Server();

        initThreadPool();
        initConnectors();

        // make sure use J2SE class loader
        webAppContext.setParentLoaderPriority(true);

        _server.setHandler(webAppContext);
    }

    public void setWebAppContext(WebAppContext webAppContext) {
        this.webAppContext = webAppContext;
    }
}
