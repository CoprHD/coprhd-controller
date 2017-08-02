/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.sdkapi;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.univmax.AuthenticationInfo;
import com.emc.storageos.driver.univmax.RegistryHandler;
import com.emc.storageos.driver.univmax.SymConstants;
import com.emc.storageos.driver.univmax.rest.EndPoint;
import com.emc.storageos.driver.univmax.rest.JsonUtil;
import com.emc.storageos.driver.univmax.rest.ResponseWrapper;
import com.emc.storageos.driver.univmax.rest.RestClient;
import com.emc.storageos.driver.univmax.rest.UrlGenerator;
import com.emc.storageos.driver.univmax.rest.exception.FailedDeleteRestCallException;
import com.emc.storageos.driver.univmax.rest.exception.FailedGetRestCallException;
import com.emc.storageos.driver.univmax.rest.exception.FailedPostRestCallException;
import com.emc.storageos.driver.univmax.rest.exception.FailedPutRestCallException;
import com.emc.storageos.driver.univmax.rest.exception.NullResponseException;
import com.emc.storageos.driver.univmax.rest.exception.UnauthorizedException;
import com.emc.storageos.driver.univmax.rest.type.common.GenericResultImplType;
import com.emc.storageos.driver.univmax.rest.type.common.IteratorType;
import com.emc.storageos.driver.univmax.rest.type.common.ParamType;
import com.emc.storageos.driver.univmax.rest.type.common.ResultListType;
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;
import com.sun.jersey.api.client.ClientResponse;

/**
 * @author fengs5
 *
 */
public class DefaultManager {
    private static final Logger log = LoggerFactory.getLogger(DefaultManager.class);
    private final static String FROM_KEY = "from";
    private final static String TO_KEY = "to";

    RestClient client;
    LockManager lockManager;
    RegistryHandler registryHandler;
    AuthenticationInfo authenticationInfo;

    /**
     * @param driverRegistry
     * @param lockManager
     */
    public DefaultManager(Registry driverRegistry, LockManager lockManager) {
        super();
        this.lockManager = lockManager;
        this.registryHandler = new RegistryHandler(driverRegistry);
    }

    /**
     * @param client the client to set
     */
    public void setClient(RestClient client) {
        this.client = client;
    }

    /**
     * @param authenticationInfo the authenticationInfo to set
     */
    public void setAuthenticationInfo(AuthenticationInfo authenticationInfo) {
        this.authenticationInfo = authenticationInfo;
    }

    boolean initializeRestClient(String arrayId) {
        authenticationInfo = registryHandler.getAccessInfo(arrayId);
        if (authenticationInfo == null) {
            log.error("Failed to find AuthenticationInfo for array {}", arrayId);
            return false;
        }
        client = new RestClient(authenticationInfo.getProtocol(), authenticationInfo.getHost(), authenticationInfo.getPort(),
                authenticationInfo.getUserName(),
                authenticationInfo.getPassword());

        return true;
    }

    List<String> genUrlFillersWithSn(String... fillers) {
        List<String> urlFillers = genUrlFillers(fillers);
        urlFillers.add(0, authenticationInfo.getSn());
        return urlFillers;
    }

    List<String> genUrlFillers(String... fillers) {
        List<String> urlFillers = new ArrayList<>();
        urlFillers.addAll(Arrays.asList(fillers));
        return urlFillers;
    }

    void appendExceptionMessage(GenericResultImplType responseBean, String template, Object... params) {
        responseBean.setHttpCode(SymConstants.StatusCode.EXCEPTION);
        responseBean.setMessage(String.format(template, params));
    }

    /**
     * Process get request.
     * 
     * @param url
     * @param responseClazzType
     * @return
     */
    <T extends GenericResultImplType> ResponseWrapper<T> get(String url, Type responseClazzType) {
        ClientResponse response = null;
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<>();
        try {
            log.debug("request url as :GET {}", url);
            response = client.get(url);
            processResponse(url, response, responseClazzType, responseWrapper);

        } catch (Exception e) {
            log.error("Exception happened during calling get rest call {}", e);
            responseWrapper.setException(new FailedGetRestCallException(e));
        } finally {
            closeResponse(response);
        }

        return responseWrapper;

    }

    /**
     * Process list request.
     * 
     * @param url
     * @param responseClazzType
     * @return
     */
    <T extends GenericResultImplType> ResponseWrapper<T> list(String url, Type responseClazzType) {
        ClientResponse response = null;
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<>();
        try {
            log.debug("request url as :LIST {}", url);
            response = client.get(url);
            processIteratorResponse(url, response, responseClazzType, responseWrapper);

        } catch (Exception e) {
            log.error("Exception happened during calling get rest call {}", e);
            responseWrapper.setException(new FailedGetRestCallException(e));
        } finally {
            closeResponse(response);
        }

        return responseWrapper;

    }

    /**
     * Process post request.
     * 
     * @param endPoint
     * @param param
     * @param responseClazzType
     * @return
     */
    <T extends GenericResultImplType> ResponseWrapper<T> post(String endPoint, ParamType param, Type responseClazzType) {
        ClientResponse response = null;
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<>();
        try {
            log.debug("request url as :POST {}", endPoint);
            response = client.post(endPoint, param.toJsonString());
            processResponse(endPoint, response, responseClazzType, responseWrapper);

        } catch (Exception e) {
            log.error("Exception happened during calling post rest call {}", e);
            responseWrapper.setException(new FailedPostRestCallException(e));
        } finally {
            closeResponse(response);
        }

        return responseWrapper;

    }

    /**
     * Process put request.
     * 
     * @param url
     * @param params
     * @param responseClazzType
     * @return
     */
    <T extends GenericResultImplType> ResponseWrapper<T> put(String url, ParamType params, Type responseClazzType) {
        ClientResponse response = null;
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<>();
        try {
            log.debug("request url as :PUT {}", url);
            log.info("Body: {}", params.toJsonString());
            response = client.put(url, params.toJsonString());
            processResponse(url, response, responseClazzType, responseWrapper);

        } catch (Exception e) {
            log.error("Exception happened during calling put rest call {}", e);
            responseWrapper.setException(new FailedPutRestCallException(e));
        } finally {
            closeResponse(response);
        }

        return responseWrapper;

    }

    /**
     * Process delete request.
     * 
     * @param url
     * @param responseClazzType
     * @return
     */
    <T extends GenericResultImplType> ResponseWrapper<T> delete(String url, Type responseClazzType) {
        ClientResponse response = null;
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<>();
        try {
            log.debug("request url as :DELETE {}", url);
            response = client.delete(url);
            processResponse(url, response, responseClazzType, responseWrapper);

        } catch (Exception e) {
            log.error("Exception happened during calling delete rest call {}", e);
            responseWrapper.setException(new FailedDeleteRestCallException(e));
        } finally {
            closeResponse(response);
        }

        return responseWrapper;

    }

    /**
     * Call iterator to list resources.
     * 
     * @param iteratorId
     * @param from
     * @param to
     * @return
     */
    <T extends GenericResultImplType> ResultListType<T> getNextPageForIterator(String iteratorId, int from, int to,
            Type responseClazzType) {

        Map<String, String> filters = new HashMap<>();
        filters.put(FROM_KEY, String.valueOf(from));
        filters.put(TO_KEY, String.valueOf(to));
        String url = UrlGenerator.genUrl(EndPoint.Common.LIST_RESOURCE, genUrlFillers(iteratorId), filters);

        @SuppressWarnings("rawtypes")
        ResponseWrapper<ResultListType> responseWrapper = get(url, responseClazzType);
        @SuppressWarnings("unchecked")
        ResultListType<T> responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            log.error("Exception happened during listing resources:{}", responseWrapper.getException());
            responseBean = new ResultListType<>();
            appendExceptionMessage(responseBean, "Exception happened during listing resources:%s",
                    responseWrapper.getException());
            return responseBean;
        }

        if (!responseBean.isSuccessfulStatus()) {
            log.error("{}: Failed to list resources with error: {}", responseBean.getHttpCode(),
                    responseBean.getMessage());
        }
        log.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

    /**
     * Get the left resources of Iterator.
     * 
     * @param iterator
     * @param responseClassType
     * @return
     */
    <T extends GenericResultImplType> List<T> getLeftAllResourcesOfIterator(IteratorType<T> iterator, Type responseClassType) {
        int total = iterator.getCount();
        int pageSize = iterator.getMaxPageSize();
        int left = total - pageSize;
        List<T> resourceList = new ArrayList<>();
        // get the left resources
        int from = pageSize + 1;
        int to = from - 1;
        while (left > 0) {
            if (left > pageSize) {
                to += pageSize;
                left = left - pageSize;
            } else {
                to += left;
                left = 0;
            }
            ResultListType<T> resultList = getNextPageForIterator(iterator.getId(), from, to, responseClassType);
            resourceList.addAll(processResultListType(resultList));

            from = to + 1;

        }

        return resourceList;
    }

    /**
     * Get all resources of iterator.
     * 
     * @param iterator
     * @param responseClassType
     * @return
     */
    <T extends GenericResultImplType> List<T> getAllResourcesOfItatrator(IteratorType<T> iterator, Type responseClassType) {
        List<T> resourceList = new ArrayList<>();
        resourceList.addAll(iterator.fetchAllResults());
        resourceList.addAll(getLeftAllResourcesOfIterator(iterator, responseClassType));

        return resourceList;
    }

    /**
     * Print error message out.
     * 
     * @param responseBean
     */
    void printErrorMessage(GenericResultImplType responseBean) {
        if (!responseBean.isSuccessfulStatus()) {
            log.error("httpCode {}: {}", responseBean.getHttpCode(), responseBean.getMessage());
        }
    }

    private void closeResponse(ClientResponse response) {
        if (response != null) {
            response.close();
        }
    }

    private <T extends GenericResultImplType> void processResponse(String url, ClientResponse response, Type responseClazzType,
            ResponseWrapper<T> responseWrapper) throws JSONException {
        log.debug("response as :{}", response);
        if (response == null) {
            responseWrapper.setException(new NullResponseException(String.format("Null Response meet during calling %s", url)));
            return;
        }
        int status = response.getStatus();
        if (ClientResponse.Status.UNAUTHORIZED.getStatusCode() == status) {
            // 401
            responseWrapper.setException(new UnauthorizedException("401: Unauthorized"));
            return;
        }
        JSONObject responseJson = new JSONObject();
        if (ClientResponse.Status.NO_CONTENT.getStatusCode() != status) {
            responseJson = response.getEntity(JSONObject.class);
        }
        T bean = JsonUtil.fromJson((responseJson.toString()), responseClazzType);
        bean.setHttpCode(status);
        responseWrapper.setResponseBean(bean);
    }

    private <T extends GenericResultImplType> void processIteratorResponse(String url, ClientResponse response, Type responseClazzType,
            ResponseWrapper<T> responseWrapper) {
        log.debug("response as :{}", response);
        if (response == null) {
            responseWrapper.setException(new NullResponseException(String.format("Null Response meet during calling %s", url)));
            return;
        }
        int status = response.getStatus();
        if (ClientResponse.Status.UNAUTHORIZED.getStatusCode() == status) {
            // 401
            responseWrapper.setException(new UnauthorizedException("401: Unauthorized"));
            return;
        }
        JSONObject responseJson = response.getEntity(JSONObject.class);
        log.debug("Jsonobject as : {}", responseJson);
        IteratorType<T> beanIterator = JsonUtil.fromJson(responseJson.toString(), responseClazzType);
        beanIterator.setHttpCode(status);
        responseWrapper.setResponseBeanIterator(beanIterator);
    }

    private <T extends GenericResultImplType> List<T> processResultListType(ResultListType<T> resultList) {
        if (resultList.isSuccessfulStatus()) {
            return resultList.getResult();
        }
        log.error("Error happened:{}", resultList.getResult());
        return new ArrayList<>();
    }

}
