/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.platform;

import com.emc.sa.util.SystemProperties;
import com.emc.storageos.coordinator.common.Service;
import com.google.common.collect.Lists;

import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.WebAppContext;
import org.springframework.beans.factory.annotation.Required;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.net.ssl.SSLContext;

import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Wraps the Jetty Server so that we can set it up from Spring
 * 
 * @author dmaddison
 */
public class StorageApiWebServer {
    private static final Logger LOG = Logger.getLogger(StorageApiWebServer.class);

    private static final String KEYSTORE_PATH = "${platform.home}/conf/keystore";
    private static final String WAR_PATH = "${platform.home}/lib/storageos-sasvcapi";

    private Server server;
    private Service serviceInfo;
    private String[] ciphers;
    private String keystoreKey;

    @PostConstruct
    public void start() {
        try {
            if (server == null) {
                initServer();
                server.start();
                LOG.info("Started StorageAPI Server on " + serviceInfo.getEndpoint().getHost() + ":" + serviceInfo.getEndpoint().getPort());
            }
            else {
                LOG.info("StorageAPI Server already created, ignoring start");
            }
        } catch (Exception e) {
            LOG.error("Error starting StorageAPI WebServer", e);
            throw new RuntimeException("Error starting StorageAPI WebServer", e);
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (server != null) {
                server.stop();
            }
            LOG.info("Stopped StorageAPI Server");
        } catch (Exception e) {
            LOG.error("Error stopping StorageAPI WebServer", e);
            throw new RuntimeException("Error stopping StorageAPI WebServer", e);
        }
    }

    @Required
    public void setServiceInfo(com.emc.storageos.coordinator.common.Service service) {
        serviceInfo = service;
    }

    @Required
    @SuppressWarnings("pmd:ArrayIsStoredDirectly")
    public void setCiphers(String[] ciphers) {
        this.ciphers = ciphers; //NOSONAR ("Suppressing sonar violation on user-supplied array is stored directly")
    }

    @Required
    public void setKeystoreKey(String keystoreKey) {
        this.keystoreKey = keystoreKey;
    }

    private void initServer() {
        server = new Server();

        // Warn if there are any Ciphers that are not supported
        try {
            List<String> supportedCipherSuites = Lists.newArrayList(SSLContext.getDefault().getSocketFactory().getSupportedCipherSuites());

            for (String chosenCipher : ciphers) {
                if (!supportedCipherSuites.contains(chosenCipher)) {
                    LOG.warn("Cipher Suite Not Supported:" + chosenCipher);
                }
            }
        } catch (NoSuchAlgorithmException e) {
            LOG.error("Error checking Cipher Suites", e);
        }

        SslContextFactory sslFac = new SslContextFactory();
        sslFac.setIncludeCipherSuites(ciphers);
        sslFac.setKeyStorePath(SystemProperties.resolve(KEYSTORE_PATH));
        sslFac.setKeyStorePassword(keystoreKey);

        SslSelectChannelConnector connector = new SslSelectChannelConnector(sslFac);
        connector.setPort(serviceInfo.getEndpoint().getPort());

        server.addConnector(connector);
        server.setSendServerVersion(false);

        WebAppContext context = new WebAppContext();
        context.setContextPath("/api");
        context.setWar(SystemProperties.resolve(WAR_PATH));

        server.setHandler(context);
    }
}
