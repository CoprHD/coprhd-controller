/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.restengine;

import static com.google.json.JsonSanitizer.sanitize;

import java.lang.reflect.Type;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.vmax3.exception.FailedDeleteRestCallException;
import com.emc.storageos.driver.vmax3.exception.FailedGetRestCallException;
import com.emc.storageos.driver.vmax3.exception.FailedPostRestCallException;
import com.emc.storageos.driver.vmax3.exception.FailedPutRestCallException;
import com.emc.storageos.driver.vmax3.exception.NullResponseException;
import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;
import com.emc.storageos.driver.vmax3.smc.basetype.IParameter;
import com.emc.storageos.driver.vmax3.smc.basetype.IResponse;
import com.emc.storageos.driver.vmax3.smc.basetype.ResponseWrapper;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.IteratorType;
import com.emc.storageos.driver.vmax3.utils.JsonParser;
import com.google.gson.Gson;
import com.sun.jersey.api.client.ClientResponse;

public class RestEngine_old {
    private static final Logger LOG = LoggerFactory.getLogger(RestClient_old.class);

    private AuthenticationInfo authenticationInfo;
    private boolean isVerifyCertificate = true;
    private RestClient_old restClient;

    /**
     * @param authenticationInfo
     */
    public RestEngine_old(AuthenticationInfo authenticationInfo) {
        super();
        this.authenticationInfo = authenticationInfo;
        this.restClient = new RestClient_old(this.authenticationInfo, isVerifyCertificate);
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
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<T>();
        try {
            response = restClient.get(url);
            processResponse(url, response, clazz, responseWrapper);

        } catch (Exception e) {
            LOG.error("Exception happened during calling get rest call {}", e);
            responseWrapper.setException(new FailedGetRestCallException(e));
        } finally {
            if (response != null) {
                response.close();
            }
        }

        return responseWrapper;

    }

    public <T extends IResponse> ResponseWrapper<T> list(String url, Class<T> clazz, Type responseClazzType) {
        ClientResponse response = null;
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<T>();
        try {
            response = restClient.get(url);
            processIteratorResponse(url, response, clazz, responseClazzType, responseWrapper);

        } catch (Exception e) {
            LOG.error("Exception happened during calling get rest call {}", e);
            responseWrapper.setException(new FailedGetRestCallException(e));
        } finally {
            if (response != null) {
                response.close();
            }
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
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<T>();
        try {
            response = restClient.post(url, params.bean2Json());
            processResponse(url, response, clazz, responseWrapper);

        } catch (Exception e) {
            LOG.error("Exception happened during calling post rest call {}", e);
            responseWrapper.setException(new FailedPostRestCallException(e));
        } finally {
            if (response != null) {
                response.close();
            }
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
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<T>();
        try {
            response = restClient.put(url, params.bean2Json());
            processResponse(url, response, clazz, responseWrapper);

        } catch (Exception e) {
            LOG.error("Exception happened during calling put rest call {}", e);
            responseWrapper.setException(new FailedPutRestCallException(e));
        } finally {
            if (response != null) {
                response.close();
            }
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
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<T>();
        try {
            response = restClient.delete(url);
            processResponse(url, response, clazz, responseWrapper);

        } catch (Exception e) {
            LOG.error("Exception happened during calling delete rest call {}", e);
            responseWrapper.setException(new FailedDeleteRestCallException(e));
        } finally {
            if (response != null) {
                response.close();
            }
        }

        return responseWrapper;

    }

    private <T extends IResponse> void processResponse(String url, ClientResponse response, Class<T> clazz,
            ResponseWrapper<T> responseWrapper) {
        if (response == null) {
            responseWrapper.setException(new NullResponseException(String.format("Null Response meet during calling %s", url)));
            return;
        }
        String respnseString = response.getEntity(String.class);
        int status = response.getStatus();
        T bean = JsonParser.parseJson2Bean((respnseString), clazz);
        bean.setHttpStatusCode(status);
        responseWrapper.setResponseBean(bean);
    }

    private <T extends IResponse> void processIteratorResponse(String url, ClientResponse response, Class<T> clazz, Type responseClazzType,
            ResponseWrapper<T> responseWrapper) {
        if (response == null) {
            responseWrapper.setException(new NullResponseException(String.format("Null Response meet during calling %s", url)));
            return;
        }
        String respnseString = response.getEntity(String.class);
        int status = response.getStatus();
        IteratorType<T> beanIterator = new Gson().fromJson(sanitize(respnseString), responseClazzType);
        beanIterator.setHttpStatusCode(status);
        responseWrapper.setResponseBeanIterator(beanIterator);
    }

}
