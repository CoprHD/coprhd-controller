/*
 * Copyright (c) 2012-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security;

import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.session.AbstractSessionManager;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.CompositeFilter;
import org.springframework.web.filter.DelegatingFilterProxy;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.security.authentication.SecurityDisablerFilter;
import com.emc.storageos.security.keystore.impl.KeyStoreUtil;
import com.emc.storageos.security.keystore.impl.KeystoreEngine;
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ResourceFilterFactory;
import com.sun.jersey.spi.container.servlet.ServletContainer;

/**
 * Base class for services including SSL connectors setup.
 * Some common filter and handler work is there in initServer().
 * Derived class can override initServer() to alter the way filters, handlers
 * and other specifics are getting handled.
 */
public abstract class AbstractSecuredWebServer {

    private final Logger _log = LoggerFactory.getLogger(getClass());

    private String _unsecurePort;
    private String _securePort;

    private String _bindAddress;
    private String _httpBindAddress;
    protected SelectChannelConnector _unsecuredConnector = null;
    protected SslSelectChannelConnector _securedConnector = null;
    protected Server _server;
    protected Application _app;
    protected DbClient _dbClient;
    private ResourceFilterFactory _resourceFilterFactory;
    private Boolean _disableSSL = false;
    private Boolean _disableHTTP = false;
    protected CompositeFilter _secFilters;
    private SecurityDisablerFilter _disablingFilter;
    private ContainerResponseFilter _responseFilter;
    private Service _serviceInfo;
    private String[] _ciphers;
    protected ServletContextHandler servletHandler;
    private CoordinatorClient _coordinatorClient;

    private ThreadPool threadPool;
    private Integer lowResourcesConnections;
    private Integer lowResourcesMaxIdleTime;
    private Integer minQueueThreads;
    private Integer maxQueueThreads;
    private Integer maxQueued;
    private boolean startDbClientInBackground;

    public void setUnsecuredConnector(SelectChannelConnector unsecuredConnector) {
        _unsecuredConnector = unsecuredConnector;
    }

    // Not a real issue as no write in class
    public void setCiphersToInclude(String[] ciphers) { // NOSONAR ("Suppressing: The user-supplied array 'ciphers' is stored directly.")
        _ciphers = ciphers;
    }

    public void setServiceInfo(Service service) {
        _serviceInfo = service;
    }

    @Autowired(required = false)
    private SecurityDisabler _disabler;

    public void setSecFilters(CompositeFilter filters) {
        _secFilters = filters;
    }

    public void setSecurityDisablingFilter(SecurityDisablerFilter filter) {
        _disablingFilter = filter;
    }

    public void setContainerResponseFilter(ContainerResponseFilter filter) {
        _responseFilter = filter;
    }

    public void setBindAddress(String bindAddress) {
        _bindAddress = bindAddress;
    }

    public void setHttpBindAddress(String bindAddress) {
        _httpBindAddress = bindAddress;
    }

    public void setSecurePort(String securePort) {
        _securePort = securePort;
    }

    public void setUnsecurePort(String unsecurePort) {
        _unsecurePort = unsecurePort;
    }

    public void setApplication(Application app) {
        _app = app;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setResourceFilterFactory(ResourceFilterFactory filterFactory) {
        _resourceFilterFactory = filterFactory;
    }

    public void setDisableSSL(Boolean disable) {
        _disableSSL = disable;
    }

    public void setDisableHTTP(Boolean disable) {
        _disableHTTP = disable;
    }

    public void setCoordinator(CoordinatorClient coord) {
        _coordinatorClient = coord;
    }

    public void setLowResourcesConnections(Integer lowResourcesConnections) {
        this.lowResourcesConnections = lowResourcesConnections;
    }

    public void setLowResourcesMaxIdleTime(Integer lowResourcesMaxIdleTime) {
        this.lowResourcesMaxIdleTime = lowResourcesMaxIdleTime;
    }

    public void setMinQueueThreads(Integer minQueueThreads) {
        this.minQueueThreads = minQueueThreads;
    }

    public void setMaxQueueThreads(Integer maxQueueThreads) {
        this.maxQueueThreads = maxQueueThreads;
    }

    public void setMaxQueued(Integer maxQueued) {
        this.maxQueued = maxQueued;
    }

    public void setStartDbClientInBackground(boolean startDbClientInBackground) {
        this.startDbClientInBackground = startDbClientInBackground;
    }

    public Server getServer() {
        return _server;
    }

    /**
     * set up the ssl connectors with strong ciphers
     * 
     * @throws Exception
     */
    protected void initConnectors() throws Exception {
        if (!_disableHTTP) {
            if (_unsecuredConnector == null) {
                _unsecuredConnector = new SelectChannelConnector();
            }
            if (_unsecurePort != null) {
                _unsecuredConnector.setPort(Integer.parseInt(_unsecurePort));
            } else {
                _unsecuredConnector.setPort(_serviceInfo.getEndpoint().getPort());
            }
            if (_httpBindAddress != null) {
                _unsecuredConnector.setHost(_httpBindAddress);
            }
            if (lowResourcesConnections != null) {
                _unsecuredConnector.setLowResourcesConnections(lowResourcesConnections);
            }
            if (lowResourcesMaxIdleTime != null) {
                _unsecuredConnector.setLowResourcesMaxIdleTime(lowResourcesMaxIdleTime);
            }
            if (threadPool != null) {
                _unsecuredConnector.setThreadPool(threadPool);
            }
            _server.addConnector(_unsecuredConnector);
        }
        if (!_disableSSL) {
            SslContextFactory sslFac = new SslContextFactory();
            sslFac.setIncludeCipherSuites(_ciphers);

            KeyStore ks = KeyStoreUtil.getViPRKeystore(_coordinatorClient);
            _log.debug("The certificates in Jetty is {}. ", ks.getCertificateChain(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS));

            sslFac.setCertAlias(KeystoreEngine.ViPR_KEY_AND_CERTIFICATE_ALIAS);
            sslFac.setKeyStore(ks);
            _securedConnector = new SslSelectChannelConnector(sslFac);
            if (_securePort != null) {
                _securedConnector.setPort(Integer.parseInt(_securePort));
            } else {
                _securedConnector.setPort(_serviceInfo.getEndpoint().getPort());
            }
            if (_bindAddress != null) {
                _securedConnector.setHost(_bindAddress);
            }
            if (lowResourcesConnections != null) {
                _securedConnector.setLowResourcesConnections(lowResourcesConnections);
            }
            if (lowResourcesMaxIdleTime != null) {
                _securedConnector.setLowResourcesMaxIdleTime(lowResourcesMaxIdleTime);
            }
            if (threadPool != null) {
                _securedConnector.setThreadPool(threadPool);
            }
            _server.addConnector(_securedConnector);
        }
        _server.setSendServerVersion(false);
    }

    protected void initThreadPool() {
        if (minQueueThreads == null && maxQueueThreads == null && maxQueued == null) {
            return;
        }

        QueuedThreadPool tp = new QueuedThreadPool();
        if (minQueueThreads != null) {
            tp.setMinThreads(minQueueThreads);
        }
        if (maxQueueThreads != null) {
            tp.setMaxThreads(maxQueueThreads);
        }
        if (maxQueued != null) {
            tp.setMaxQueued(maxQueued);
        }
        threadPool = tp;
    }

    /**
     * Initialize server handlers, rest resources.
     * 
     * @throws Exception
     */
    protected void initServer() throws Exception {
        _server = new Server();
        initThreadPool();
        initConnectors();

        // AuthN servlet filters
        servletHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        servletHandler.setContextPath("/");
        _server.setHandler(servletHandler);

        ((AbstractSessionManager) servletHandler.getSessionHandler().getSessionManager()).setUsingCookies(false);

        if (_disabler != null) {
            final FilterHolder securityFilterHolder = new FilterHolder(
                    new DelegatingFilterProxy(_disablingFilter));
            servletHandler.addFilter(securityFilterHolder, "/*", FilterMapping.REQUEST);
            _log.warn("security checks are disabled... skipped adding security filters");
        } else {
            final FilterHolder securityFilterHolder = new FilterHolder(new DelegatingFilterProxy(_secFilters));
            servletHandler.addFilter(securityFilterHolder, "/*", FilterMapping.REQUEST);
        }

        // Add the REST resources
        if (_app != null) {
            ResourceConfig config = new DefaultResourceConfig();
            config.add(_app);
            Map<String, MediaType> type = config.getMediaTypeMappings();
            type.put("json", MediaType.APPLICATION_JSON_TYPE);
            type.put("xml", MediaType.APPLICATION_XML_TYPE);
            servletHandler.addServlet(new ServletHolder(new ServletContainer(config)), "/*");
            // AuthZ resource filters
            Map<String, Object> props = new HashMap<String, Object>();
            props.put(ResourceConfig.PROPERTY_RESOURCE_FILTER_FACTORIES, _resourceFilterFactory);

            // Adding the ContainerResponseFilter
            props.put(ResourceConfig.PROPERTY_CONTAINER_RESPONSE_FILTERS, _responseFilter);
            config.setPropertiesAndFeatures(props);
        }
        if (_dbClient != null) {
            // in some cases, like syssvc, we don't want the service to be blocked by dbsvc startup.
            // Otherwise there could be a dependency loop between services.
            if (startDbClientInBackground) {
                _log.info("starting dbclient in background");
                new Thread() {
                    public void run() {
                        _dbClient.start();
                    }
                }.start();
            } else {
                _log.info("starting dbclient");
                _dbClient.start();
            }
        }
    }
}
