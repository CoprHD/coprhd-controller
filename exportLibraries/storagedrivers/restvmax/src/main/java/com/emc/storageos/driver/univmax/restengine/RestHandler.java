/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.restengine;

import static com.google.json.JsonSanitizer.sanitize;

import java.lang.reflect.Type;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.univmax.exception.FailedDeleteRestCallException;
import com.emc.storageos.driver.univmax.exception.FailedGetRestCallException;
import com.emc.storageos.driver.univmax.exception.FailedPostRestCallException;
import com.emc.storageos.driver.univmax.exception.FailedPutRestCallException;
import com.emc.storageos.driver.univmax.exception.NullResponseException;
import com.emc.storageos.driver.univmax.smc.basetype.AuthenticationInfo;
import com.emc.storageos.driver.univmax.smc.basetype.IParameter;
import com.emc.storageos.driver.univmax.smc.basetype.IResponse;
import com.emc.storageos.driver.univmax.smc.basetype.ResponseWrapper;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.IteratorType;
import com.emc.storageos.driver.univmax.utils.JsonParser;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.client.urlconnection.HTTPSProperties;
import com.sun.jersey.client.urlconnection.URLConnectionClientHandler;

public class RestHandler {
    private static final Logger LOG = LoggerFactory.getLogger(RestHandler.class);
    private static final String TLS = "TLS";
    private AuthenticationInfo authenticationInfo;
    private boolean isVerifyCertificate = true;
    private RestClient restClient;

    /**
     * @param authenticationInfo
     */
    public RestHandler(AuthenticationInfo authenticationInfo) {
        this.authenticationInfo = authenticationInfo;
        ClientHandler handler = new URLConnectionClientHandler();
        Client client = new Client(handler, configureClient());
        this.restClient = new RestClient(this.authenticationInfo.getUserName(), this.authenticationInfo.getPassword(), client);
    }

    public RestClient getRestClient() {
        return restClient;
    }

    public void close() {
        restClient.close();
    }

    private ClientConfig configureClient() {
        TrustManager[] certs = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                        // Do nothing
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                        // Do nothing
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };

        SSLContext context = null;
        try {
            context = SSLContext.getInstance(TLS);
            context.init(null, certs, new SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            LOG.error("Failed to initialize TLS with exception {}", e);
            return null;
        }
        HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());

        ClientConfig config = new DefaultClientConfig();
        config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES,
                new HTTPSProperties((s, sslSession) -> isVerifyCertificate, context));
        config.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);

        return config;
    }

    /**
     * Process get request.
     * 
     * @param url
     * @param Class<T> clazz, response bean
     * @return ResponseWrapper<T>
     */
    public <T extends IResponse> ResponseWrapper<T> get(String url, Class<T> clazz) {
        ClientResponse response = null;
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<>();
        try {
            LOG.debug("request url as :GET {}", url);
            response = restClient.get(URI.create(url));
            processResponse(url, response, clazz, responseWrapper);

        } catch (Exception e) {
            LOG.error("Exception happened during calling get rest call {}", e);
            responseWrapper.setException(new FailedGetRestCallException(e));
        } finally {
            restClient.closeResponse(response);
        }

        return responseWrapper;

    }

    public <T extends IResponse> ResponseWrapper<T> list(String url, Type responseClazzType) {
        ClientResponse response = null;
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<>();
        try {
            LOG.debug("request url as :LIST {}", url);
            response = restClient.get(URI.create(url));
            processIteratorResponse(url, response, responseClazzType, responseWrapper);

        } catch (Exception e) {
            LOG.error("Exception happened during calling get rest call {}", e);
            responseWrapper.setException(new FailedGetRestCallException(e));
        } finally {
            restClient.closeResponse(response);
        }

        return responseWrapper;

    }

    /**
     * Process post request.
     * 
     * @param url
     * @param IParameter params, request content
     * @param Class<T> clazz, response bean
     * @return ResponseWrapper<T>
     */
    public <T extends IResponse> ResponseWrapper<T> post(String url, IParameter params, Class<T> clazz) {
        ClientResponse response = null;
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<>();
        try {
            LOG.debug("request url as :POST {}", url);
            response = restClient.post(URI.create(url), params.bean2Json());
            processResponse(url, response, clazz, responseWrapper);

        } catch (Exception e) {
            LOG.error("Exception happened during calling post rest call {}", e);
            responseWrapper.setException(new FailedPostRestCallException(e));
        } finally {
            restClient.closeResponse(response);
        }

        return responseWrapper;

    }

    /**
     * Process put request.
     * 
     * @param url
     * @param IParameter params, request content
     * @param Class<T> clazz, response bean
     * @return ResponseWrapper<T>
     */
    public <T extends IResponse> ResponseWrapper<T> put(String url, IParameter params, Class<T> clazz) {
        ClientResponse response = null;
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<>();
        try {
            LOG.debug("request url as :PUT {}", url);
            LOG.info("Body: {}", params.bean2Json());
            response = restClient.put(URI.create(url), params.bean2Json());
            processResponse(url, response, clazz, responseWrapper);

        } catch (Exception e) {
            LOG.error("Exception happened during calling put rest call {}", e);
            responseWrapper.setException(new FailedPutRestCallException(e));
        } finally {
            restClient.closeResponse(response);
        }

        return responseWrapper;

    }

    /**
     * Process delete request.
     * 
     * @param url
     * @param Class<T> clazz, response bean
     * @return ResponseWrapper<T>
     */
    public <T extends IResponse> ResponseWrapper<T> delete(String url, Class<T> clazz) {
        ClientResponse response = null;
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<>();
        try {
            LOG.debug("request url as :DELETE {}", url);
            response = restClient.delete(URI.create(url));
            processResponse(url, response, clazz, responseWrapper);

        } catch (Exception e) {
            LOG.error("Exception happened during calling delete rest call {}", e);
            responseWrapper.setException(new FailedDeleteRestCallException(e));
        } finally {
            restClient.closeResponse(response);
        }

        return responseWrapper;

    }

    private <T extends IResponse> void processResponse(String url, ClientResponse response, Class<T> clazz,
            ResponseWrapper<T> responseWrapper) {
        LOG.debug("response as :{}", response);
        if (response == null) {
            responseWrapper.setException(new NullResponseException(String.format("Null Response meet during calling %s", url)));
            return;
        }
        JSONObject respnseJson = response.getEntity(JSONObject.class);
        int status = response.getStatus();
        T bean = JsonParser.parseJson2Bean((respnseJson.toString()), clazz);
        bean.setHttpStatusCode(status);
        responseWrapper.setResponseBean(bean);
    }

    private <T extends IResponse> void processIteratorResponse(String url, ClientResponse response, Type responseClazzType,
            ResponseWrapper<T> responseWrapper) {
        LOG.debug("response as :{}", response);
        if (response == null) {
            responseWrapper.setException(new NullResponseException(String.format("Null Response meet during calling %s", url)));
            return;
        }
        JSONObject respnseJson = response.getEntity(JSONObject.class);
        int status = response.getStatus();
        IteratorType<T> beanIterator = new Gson().fromJson(sanitize(respnseJson.toString()), responseClazzType);
        beanIterator.setHttpStatusCode(status);
        responseWrapper.setResponseBeanIterator(beanIterator);
    }

}
