/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmax3.restengine;

import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;
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

public class RestClient_old {

    private static final Logger LOG = LoggerFactory.getLogger(RestClient_old.class);

    private static final String AUTHORIZATION = "Authorization";
    private static final String BASIC = "Basic ";
    private static final String APPLICATION_JSON = "application/json";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String ACCEPT = "Accept";
    private static final String TLS = "TLS";

    private AuthenticationInfo authenticationInfo;
    private boolean isVerifyCertificate;

    public RestClient_old(AuthenticationInfo authenticationInfo, boolean isVerifyCertificate) {
        this.authenticationInfo = authenticationInfo;
        this.isVerifyCertificate = isVerifyCertificate;
    }

    private ClientConfig configureClient() {
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
            context = SSLContext.getInstance(TLS);
            context.init(null, certs, new SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            // TODO throw cust Exception
            LOG.error("Failed to initialize TLS.");
            return null;
        }
        HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());

        ClientConfig config = new DefaultClientConfig();
        config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES,
                new HTTPSProperties((s, sslSession) -> isVerifyCertificate, context));
        config.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);

        return config;
    }

    private ClientResponse getJson(Client client, URI url, String authToken) {
        WebResource r = client.resource(url);
        return r.header(CONTENT_TYPE, APPLICATION_JSON)
                .header(ACCEPT, APPLICATION_JSON)
                .header("X-HP3PAR-WSAPI-SessionKey", authToken)
                .get(ClientResponse.class);
    }

    private String getAuthFieldValue() {
        return BASIC
                + Base64.getEncoder()
                        .encodeToString((authenticationInfo.getUserName() + ":" + authenticationInfo.getPassword()).getBytes());
    }

    private Builder buildClient(String url) {
        ClientHandler handler = new URLConnectionClientHandler();
        Client client = new Client(handler, configureClient());
        WebResource webResource = client.resource(url);
        return webResource.header(CONTENT_TYPE, APPLICATION_JSON)
                .header(ACCEPT, APPLICATION_JSON)
                .header(AUTHORIZATION, getAuthFieldValue());
    }

    public ClientResponse get(String url) {

        LOG.debug("request url as :GET {}", url);
        ClientResponse cr = buildClient(url)
                .get(ClientResponse.class);
        LOG.debug("response as :{}", cr);
        return cr;
    }

    public ClientResponse delete(String url) {
        LOG.debug("request url as :DELETE {}", url);
        ClientResponse cr = buildClient(url)
                .delete(ClientResponse.class);
        LOG.debug("response as :{}", cr);
        return cr;
    }

    public ClientResponse post(String url, String restParam) {
        LOG.debug("request url as :POST {}", url);
        LOG.debug("Post content as :{}", restParam);
        ClientResponse cr = buildClient(url)
                .post(ClientResponse.class, restParam);
        LOG.debug("response as :{}", cr);
        return cr;
    }

    public ClientResponse put(String url, String restParam) {
        LOG.debug("request url as :PUT {}", url);
        LOG.debug("Put content as :{}", restParam);
        ClientResponse cr = buildClient(url)
                .put(ClientResponse.class, restParam);
        LOG.debug("response as :{}", cr);
        return cr;
    }
}
