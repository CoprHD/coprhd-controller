/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.keystore;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.storageos.services.util.EnvConfig;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.session.AbstractSessionManager;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import com.emc.storageos.security.keystore.impl.KeyCertificateEntry;
import com.emc.storageos.security.keystore.impl.KeyCertificatePairGenerator;
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;

public class TestWebServer {

    private SslSelectChannelConnector _securedConnector = null;
    public static final int _securePort = 9930;
    public final KeyCertificateEntry _keyAndCert;
    public static final String _keystorePassword = EnvConfig.get("sanity", "keystore.password"); // NOSONAR ("Suppressing: removing this hard-coded password since it's just the name of attribute")
    private Server _server;
    private final Application _app = new TestApplication();
    private final String[] _ciphers = { "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
            "TLS_DHE_RSA_WITH_AES_128_CBC_SHA", "TLS_RSA_WITH_AES_128_CBC_SHA",
            "TLS_DHE_DSS_WITH_AES_256_CBC_SHA", "TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
    "TLS_RSA_WITH_AES_256_CBC_SHA" };

    public TestWebServer(KeyCertificateEntry entry) {
        _keyAndCert = entry;
    }

    /**
     * set up the ssl connectors with strong ciphers
     * 
     * @throws Exception
     */
    private void initConnectors() throws Exception {
        SslContextFactory sslFac = new SslContextFactory();
        sslFac.setIncludeCipherSuites(_ciphers);
        KeyStore ks = loadKeystore();
        sslFac.setKeyStore(ks);
        sslFac.setKeyStorePassword(_keystorePassword);
        sslFac.setKeyManagerPassword(_keystorePassword);
        sslFac.setTrustStorePassword(_keystorePassword);
        _securedConnector = new SslSelectChannelConnector(sslFac);
        _securedConnector.setPort(_securePort);
        _server.addConnector(_securedConnector);
        _server.setSendServerVersion(false);
    }

    /**
     * @return
     * @throws Exception
     */
    private KeyStore loadKeystore() throws Exception {
        // initializing keystore
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, _keystorePassword.toCharArray());

        PrivateKey key =
                KeyCertificatePairGenerator.loadPrivateKeyFromBytes(_keyAndCert.getKey());

        ks.setKeyEntry("server_key_alias", key, _keystorePassword.toCharArray(),
                _keyAndCert.getCertificateChain());

        return ks;
    }

    /**
     * Initialize server handlers, rest resources.
     * 
     * @throws Exception
     */
    private void initServer() throws Exception {
        _server = new Server();
        initConnectors();

        // AuthN servlet filters
        ServletContextHandler rootHandler =
                new ServletContextHandler(ServletContextHandler.SESSIONS);
        rootHandler.setContextPath("/");
        _server.setHandler(rootHandler);

        ((AbstractSessionManager) rootHandler.getSessionHandler().getSessionManager())
        .setUsingCookies(false);


        // Add the REST resources
        if (_app != null) {
            ResourceConfig config = new DefaultResourceConfig();
            config.add(_app);
            Map<String, MediaType> type = config.getMediaTypeMappings();
            type.put(MediaType.TEXT_PLAIN, MediaType.TEXT_PLAIN_TYPE);
            rootHandler.addServlet(new ServletHolder(new ServletContainer(config)),
                    "/*");
        }

    }

    public synchronized void start() throws Exception {
        initServer();
        _server.start();
    }

    public synchronized void stop() throws Exception {
        _server.stop();
    }


    public class TestApplication extends Application {
        private final Set<Object> _resource;

        public TestApplication() {
            _resource = new HashSet<Object>();
            _resource.add(new TestResource());
        }

        @Override
        public Set<Object> getSingletons() {
            return _resource;
        }
    }

    @Path("/test")
    public class TestResource {

        private int count = 0;

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public Response getResponse() {
            return Response.ok(new Integer(++count).toString()).build();
        }

    }
}