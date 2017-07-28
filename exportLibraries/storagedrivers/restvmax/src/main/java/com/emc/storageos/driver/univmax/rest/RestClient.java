/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest;

import com.sun.jersey.api.client.*;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.client.urlconnection.HTTPSProperties;
import com.sun.jersey.client.urlconnection.URLConnectionClientHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class RestClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RestClient.class);

    private static String HTTP_HEADER_AUTH_FIELD = "Authorization";
    private String httpHeaderAuthFieldValue;
    public static int DEFAULT_PORT = 8443;
    private String baseURI;
    private Boolean verifyCA = false;

    public RestClient(Boolean useSSL, String host, int port, String user, String password) {
        baseURI = String.format("%s://%s:%d/univmax/restapi",
                useSSL ? "https" : "http",
                host,
                port > 0 ? port : RestClient.DEFAULT_PORT);
        httpHeaderAuthFieldValue = "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
    }

    public String getJsonString(String endPoint) {
        ClientResponse cr;
        try {
            cr = get(endPoint);
        } catch (Exception e) {
            throw new RuntimeException("path: " + baseURI + endPoint, e);
        }
        return responseToString(endPoint, null, cr);
    }

    public String deleteJsonString(String endPoint) {
        ClientResponse cr;
        try {
            cr = delete(endPoint);
        } catch (Exception e) {
            throw new RuntimeException("path: " + baseURI + endPoint, e);
        }
        return responseToString(endPoint, null, cr);
    }

    public String postJsonString(String endPoint, String restParam) {
        ClientResponse cr;
        try {
            cr = post(endPoint, restParam);
        } catch (Exception e) {
            throw new RuntimeException("path: " + baseURI + endPoint, e);
        }
        return responseToString(endPoint, restParam, cr);
    }

    public String putJsonString(String endPoint, String restParam) {
        ClientResponse cr;
        try {
            cr = put(endPoint, restParam);
        } catch (Exception e) {
            throw new RuntimeException("path: " + baseURI + endPoint, e);
        }
        return responseToString(endPoint, restParam, cr);
    }

    public ClientResponse get(String endPoint) {
        ClientHandler handler = new URLConnectionClientHandler();
        Client client = new Client(handler, configureClient(verifyCA));
        WebResource r = client.resource(baseURI + endPoint);
        return r.header("Content-Type", "application/json")
                .header(HTTP_HEADER_AUTH_FIELD, httpHeaderAuthFieldValue)
                .get(ClientResponse.class);
    }

    public ClientResponse delete(String endPoint) {
        ClientHandler handler = new URLConnectionClientHandler();
        Client client = new Client(handler, configureClient(verifyCA));
        WebResource r = client.resource(baseURI + endPoint);
        return r.header("Content-Type", "application/json")
                .header(HTTP_HEADER_AUTH_FIELD, httpHeaderAuthFieldValue)
                .delete(ClientResponse.class);
    }

    public ClientResponse post(String endPoint, String restParam) {
        ClientHandler handler = new URLConnectionClientHandler();
        Client client = new Client(handler, configureClient(verifyCA));
        WebResource r = client.resource(baseURI + endPoint);
        return r.header("Content-Type", "application/json")
                .header(HTTP_HEADER_AUTH_FIELD, httpHeaderAuthFieldValue)
                .post(ClientResponse.class, restParam);
    }

    public ClientResponse put(String endPoint, String restParam) {
        ClientHandler handler = new URLConnectionClientHandler();
        Client client = new Client(handler, configureClient(verifyCA));
        WebResource r = client.resource(baseURI + endPoint);
        return r.header("Content-Type", "application/json")
                .header(HTTP_HEADER_AUTH_FIELD, httpHeaderAuthFieldValue)
                .put(ClientResponse.class, restParam);
    }

    private static ClientConfig configureClient(boolean verifyCA) {
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
                    new HTTPSProperties(new HostnameVerifier() {
                        @Override
                        public boolean verify(String s, SSLSession sslSession) {
                            return verifyCA;
                        }
                    }, context));
        } catch (Exception e) {
            LOG.error("Failed to set HTTPS properties.");
            return null;
        }
        config.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);

        return config;
    }

    private String responseToString(String endPoint, String restParam, ClientResponse cr) {
        String msg = String.format("path:\n\t%s%s%s%s", baseURI, endPoint,
                restParam == null ? "" : "\nInput Parameter:\n\t",
                restParam == null ? "" : restParam);
        if (cr == null) {
            String err = String.format("error: No http response.\n%s", msg);
            LOG.info(err);
            throw new RuntimeException(err);
        }

        String rstr = cr.getEntity(String.class);
        int status = cr.getStatus();
        try {
            cr.close();
        } catch (Exception e) {
            LOG.info("Exception on closing client response: " + cr.toString());
        }
        if (status != 200) {
            String err = String.format("Status-Code: %d.\n%s\nResponse:\n%s", status, msg, rstr);
            LOG.info(err);
            throw new RuntimeException(err);
        }

        return rstr;
    }

    @Override
    public void close() throws Exception {
    }
}
