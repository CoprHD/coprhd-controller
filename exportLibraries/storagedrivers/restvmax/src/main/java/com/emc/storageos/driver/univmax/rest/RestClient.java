/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.concurrent.RejectedExecutionException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.client.urlconnection.HTTPSProperties;
import com.sun.jersey.client.urlconnection.URLConnectionClientHandler;

public class RestClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RestClient.class);

    private static final String APPLICATION_TYPE_VIPR = "vipr";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_TYPE = "Application-Type";
    private static final String HTTP_HEADER_AUTH_FIELD = "Authorization";

    private static final String HTTPS = "https";
    private static final String HTTP = "http";
    private String httpHeaderAuthFieldValue;
    public static final int DEFAULT_PORT = 8443;
    private String baseURI;
    private Boolean verifyCA = true;

    public RestClient(Boolean useSSL, String host, int port, String user, String password) {
        this(useSSL ? HTTPS : HTTP, host, port, user, password);
    }

    public RestClient(String protocol, String host, int port, String user, String password) {
        baseURI = String.format("%s://%s:%d/univmax/restapi",
                protocol,
                host,
                port > 0 ? port : RestClient.DEFAULT_PORT);
        httpHeaderAuthFieldValue = "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
    }

    public enum METHOD {
        GET, DELETE, POST, PUT
    }

    public String getJsonString(METHOD method, String endPoint) {
        ClientResponse cr;
        try {
            switch (method) {
                case GET:
                    cr = get(endPoint);
                    break;
                case DELETE:
                    cr = delete(endPoint);
                    break;
                default:
                    throw new IllegalArgumentException("Wrong method: " + method.name());
            }
        } catch (Exception e) {
            throw new RuntimeException(getDebugString(endPoint, null), e);
        }
        return responseToString(endPoint, null, cr);
    }

    public String getJsonString(METHOD method, String endPoint, String restParam) {
        ClientResponse cr;
        try {
            switch (method) {
                case POST:
                    cr = post(endPoint, restParam);
                    break;
                case PUT:
                    cr = put(endPoint, restParam);
                    break;
                default:
                    throw new IllegalArgumentException("Wrong method: " + method.name());
            }
        } catch (Exception e) {
            throw new RuntimeException(getDebugString(endPoint, restParam), e);
        }
        return responseToString(endPoint, restParam, cr);
    }

    @Deprecated
    public String getJsonString(String endPoint) {
        ClientResponse cr;
        try {
            cr = get(endPoint);
        } catch (Exception e) {
            throw new RuntimeException(getDebugString(endPoint, null), e);
        }
        return responseToString(endPoint, null, cr);
    }

    @Deprecated
    public String deleteJsonString(String endPoint) {
        ClientResponse cr;
        try {
            cr = delete(endPoint);
        } catch (Exception e) {
            throw new RuntimeException(getDebugString(endPoint, null), e);
        }
        return responseToString(endPoint, null, cr);
    }

    @Deprecated
    public String postJsonString(String endPoint, String restParam) {
        ClientResponse cr;
        try {
            cr = post(endPoint, restParam);
        } catch (Exception e) {
            throw new RuntimeException(getDebugString(endPoint, restParam), e);
        }
        return responseToString(endPoint, restParam, cr);
    }

    @Deprecated
    public String putJsonString(String endPoint, String restParam) {
        ClientResponse cr;
        try {
            cr = put(endPoint, restParam);
        } catch (Exception e) {
            throw new RuntimeException(getDebugString(endPoint, restParam), e);
        }
        return responseToString(endPoint, restParam, cr);
    }

    private Builder configHeader(WebResource webResource) {
        return webResource.type(MediaType.APPLICATION_JSON)
                .header(APPLICATION_TYPE, APPLICATION_TYPE_VIPR)
                .header(CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(HTTP_HEADER_AUTH_FIELD, httpHeaderAuthFieldValue);
    }

    public ClientResponse get(String endPoint) {
        ClientHandler handler = new URLConnectionClientHandler();
        Client client = new Client(handler, configureClient(verifyCA));
        WebResource r = client.resource(baseURI + endPoint);
        return configHeader(r)
                .get(ClientResponse.class);
    }

    public ClientResponse delete(String endPoint) {
        ClientHandler handler = new URLConnectionClientHandler();
        Client client = new Client(handler, configureClient(verifyCA));
        WebResource r = client.resource(baseURI + endPoint);
        return configHeader(r)
                .delete(ClientResponse.class);
    }

    public ClientResponse post(String endPoint, String restParam) {
        ClientHandler handler = new URLConnectionClientHandler();
        Client client = new Client(handler, configureClient(verifyCA));
        WebResource r = client.resource(baseURI + endPoint);
        return configHeader(r)
                .post(ClientResponse.class, restParam);
    }

    public ClientResponse put(String endPoint, String restParam) {
        ClientHandler handler = new URLConnectionClientHandler();
        Client client = new Client(handler, configureClient(verifyCA));
        WebResource r = client.resource(baseURI + endPoint);
        return configHeader(r)
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
        String msg = getDebugString(endPoint, restParam);
        if (cr == null) {
            String err = String.format("error: No http response.%n%s", msg);
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

        /*
         * According to EMC Unisphere(TM) for VMAX Version 8.4.0 REST API Concepts and Programmer's Guide:
         * One new change in Unisphere for VMAX REST API 8.4:
         * Return codes are standardized across all REST calls:
         * 200 - Success, 201 Successful and Resource object Created, 202 Accepted (asynchronous)
         * 401 - Incorrect Username or Password
         * 403 - User not Authorized to make the call
         * 404 - Object not found
         * 409 - Object already exists
         * 500 - Server Side error Check SMAS logs, services etc
         */
        if (status == ClientResponse.Status.OK.getStatusCode() ||
                status == ClientResponse.Status.CREATED.getStatusCode() ||
                status == ClientResponse.Status.ACCEPTED.getStatusCode()) {
            // 200 - Success, 201 Successful and Resource object Created, 202 Accepted (asynchronous)
            return rstr;
        }
        String err = String.format("Status-Code: %d.%n%s%nResponse:%n%s", status, msg, rstr);
        LOG.info(err);
        if (status == ClientResponse.Status.UNAUTHORIZED.getStatusCode()) {
            // 401 - Incorrect Username or Password
            throw new SecurityException(err);
        } else if (status == ClientResponse.Status.FORBIDDEN.getStatusCode()) {
            // 403 - User not Authorized to make the call
            throw new RejectedExecutionException(err);
        } else if (status == ClientResponse.Status.NOT_FOUND.getStatusCode()) {
            // 404 - Object not found
            throw new NoSuchElementException(err);
        } else if (status == ClientResponse.Status.CONFLICT.getStatusCode()) {
            // 409 - Object already exists
            throw new ConcurrentModificationException(err);
        } else if (status == ClientResponse.Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
            // 500 - Server Side error Check SMAS logs, services etc
            throw new IllegalStateException(err);
        }
        throw new RuntimeException(err);
    }

    private String getDebugString(String endPoint, String restParam) {
        String msg = String.format("path:%n\t%s%s", baseURI, endPoint);
        if (restParam != null) {
            msg = String.format("%s%nInput Parameter:%n\t%s", msg, restParam);
        }
        return msg;
    }

    @Override
    public void close() throws Exception {
    }
}