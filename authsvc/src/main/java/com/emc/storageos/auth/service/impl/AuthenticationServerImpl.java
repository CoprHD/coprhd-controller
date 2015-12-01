/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.service.impl;

import java.util.Map;

import javax.ws.rs.core.MediaType;

import com.emc.storageos.security.keystore.impl.TrustStoreLoader;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.session.AbstractSessionManager;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.DelegatingFilterProxy;

import com.emc.storageos.auth.AuthenticationManager;
import com.emc.storageos.security.password.InvalidLoginManager;
import com.emc.storageos.auth.impl.CassandraTokenManager;
import com.emc.storageos.coordinator.client.beacon.ServiceBeacon;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.security.AbstractSecuredWebServer;
import com.emc.storageos.security.authentication.AuthSvcEndPointLocator;
import com.emc.storageos.security.authentication.StorageOSUserRepository;
import com.emc.storageos.security.ssl.ViPRSSLSocketFactory;
import com.emc.storageos.security.validator.Validator;
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;

/**
 * Main class for authentication service
 */
public class AuthenticationServerImpl extends AbstractSecuredWebServer {
    private final String AUTH_DOCUMENT_ROOT = "storageos-authsvc/docs";
    private AuthenticationManager _authManager;
    private CassandraTokenManager _tokenManager;
    private InvalidLoginManager _invalidLoginManager;

    @Autowired
    private ServiceBeacon _svcBeacon;

    @Autowired
    private AuthSvcEndPointLocator _authSvcEndPointLocator;

    @Autowired
    private CoordinatorClient _coordinator;

    @Autowired
    StorageOSUserRepository _repository;

    @Autowired
    TrustStoreLoader _trustStoreLoader;
    
    @Autowired
    DrUtil _drUtil;

    public void setTrustStoreLoader(TrustStoreLoader trustStoreLoader) {
        _trustStoreLoader = trustStoreLoader;
    }

    public void setAuthManager(AuthenticationManager authManager) {
        _authManager = authManager;
    }

    public void setCassTokenManager(CassandraTokenManager tokenManager) {
        _tokenManager = tokenManager;
    }

    public void setInvalidLoginManager(InvalidLoginManager invalidLoginManager) {
        _invalidLoginManager = invalidLoginManager;
    }

    public synchronized void start() throws Exception {
        initServer();
        _server.start();
        initValidator();
        initViPRSSLSocketFactory();
        _svcBeacon.start();
        if (_drUtil.isActiveSite()) {
            _invalidLoginManager.init();
        }
    }

    public synchronized void stop() throws Exception {
        _server.stop();
        _dbClient.stop();
        _authManager.shutdown();
        if (_drUtil.isActiveSite()) {
            _invalidLoginManager.shutdown();
        }
    }

    private void initValidator() {
        Validator.setCoordinator(_coordinator);
        Validator.setAuthSvcEndPointLocator(_authSvcEndPointLocator);
        Validator.setStorageOSUserRepository(_repository);
    }

    private void initViPRSSLSocketFactory() {
        ViPRSSLSocketFactory.setCoordinatorClient(_coordinator);
    }

    @Override
    protected void initServer() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        String authDocumentRoot = loader.getResource(AUTH_DOCUMENT_ROOT).toString();
        _server = new Server();

        initConnectors();

        // Static Pages
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setWelcomeFiles(new String[] { "*" });
        resourceHandler.setResourceBase(authDocumentRoot);

        // AuthN servlet filters

        ServletContextHandler rootHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        rootHandler.setContextPath("/");
        HandlerCollection handlerCollection = new HandlerCollection();
        handlerCollection.setHandlers(new Handler[] { resourceHandler, rootHandler });
        _server.setHandler(handlerCollection);
        ((AbstractSessionManager) rootHandler.getSessionHandler().getSessionManager()).setUsingCookies(false);

        final FilterHolder securityFilterHolder = new FilterHolder(new DelegatingFilterProxy(_secFilters));
        rootHandler.addFilter(securityFilterHolder, "/*", FilterMapping.REQUEST);

        // Add the REST resources
        if (_app != null) {
            ResourceConfig config = new DefaultResourceConfig();
            config.add(_app);
            Map<String, MediaType> type = config.getMediaTypeMappings();
            type.put("json", MediaType.APPLICATION_JSON_TYPE);
            type.put("xml", MediaType.APPLICATION_XML_TYPE);
            rootHandler.addServlet(new ServletHolder(new ServletContainer(config)), "/*");
        }

        // load trust store from file to zk. must do it before authmgr started, who holds the connection with ad.
        loadTrustStoreFromLocalFiles();

        _dbClient.start();
        _tokenManager.init();
        _authManager.init();
    }

    private void loadTrustStoreFromLocalFiles() {
        _trustStoreLoader.load();
    }
}
