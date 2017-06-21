/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.restvmax.rest;

import com.sun.jersey.api.client.*;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.client.urlconnection.HTTPSProperties;
import com.sun.jersey.client.urlconnection.URLConnectionClientHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.awt.*;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class RestAPI implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RestAPI.class);
    private static final String URI_HTTPS = "https://";

    private String host;
    private String port;
    private String user;
    private String password;
    private String pathVendorPrefix;

    public enum BackendType {
        VMAX {
            String getAuthField() {
                return "Authorization";
            }
            String getAuthFieldValue(String username, String password) {
                return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            }
        };

        abstract String getAuthField();
        abstract String getAuthFieldValue(String username, String password);
    }

    public RestAPI(String host, int port, String user, String password, String pathVendorPrefix) {
        this.host = host;
        this.port = Integer.toString(port);
        this.user = user;
        this.password = password;
        this.pathVendorPrefix = pathVendorPrefix;
    }

    private static ClientConfig configureClient(boolean verify) {
        TrustManager[] certs = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                }
        };

        SSLContext context = null;
        try {
            context = SSLContext.getInstance("TLS");
            context.init(null, certs, new SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            LOG.error("Failed to initialize TLS.");
            return null;
        }
        HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());

        ClientConfig config = new DefaultClientConfig();
        try {
            config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES,
                    new HTTPSProperties((s, sslSession) -> verify, context));
        } catch (Exception e) {
            LOG.error("Failed to set HTTPS properties.");
            return null;
        }
        config.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);

        return config;
    }

    public ClientResponse getJson(Client client, URI url, String authToken) {
        WebResource r = client.resource(url);
        return r.header("Content-Type", "application/json").header("X-HP3PAR-WSAPI-SessionKey", authToken)
                .get(ClientResponse.class);
    }

    public static ClientResponse get(String path, boolean verify, BackendType backendType, String username, String password) {
        ClientHandler handler = new URLConnectionClientHandler();
        Client client = new Client(handler, configureClient(verify));
        WebResource r = client.resource(path);
        ClientResponse cr = r.header("Content-Type", "application/json")
                .header(backendType.getAuthField(), backendType.getAuthFieldValue(username, password))
                .get(ClientResponse.class);
        if (cr == null) {
            LOG.error("No response.");
            throw new NullPointerException("RESTful API: no response.");
        }

        return cr;
    }

    @Override
    public void close() {
    }
}

