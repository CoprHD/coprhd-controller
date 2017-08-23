/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.univmax.SymConstants;
import com.emc.storageos.driver.univmax.rest.exception.ClientException;
import com.emc.storageos.driver.univmax.rest.exception.FailedGetResourceException;
import com.emc.storageos.driver.univmax.rest.exception.NoResourceFoundException;
import com.emc.storageos.driver.univmax.rest.exception.NullResponseException;
import com.emc.storageos.driver.univmax.rest.exception.ServerException;
import com.emc.storageos.driver.univmax.rest.type.common.GenericResultImplType;
import com.emc.storageos.driver.univmax.rest.type.common.IteratorType;
import com.emc.storageos.driver.univmax.rest.type.common.ParamType;
import com.emc.storageos.driver.univmax.rest.type.common.ResultListType;
import com.sun.jersey.api.client.ClientResponse;

public class RestHandler {

    private static final Logger log = LoggerFactory.getLogger(RestHandler.class);
    private final static String FROM_KEY = "from";
    private final static String TO_KEY = "to";
    private RestClient client;

    public RestHandler(RestClient client) {
        this.client = client;
    }

    /**
     * Process get request.
     * 
     * @param url
     * @param responseClazzType
     * @return
     */
    public <T extends GenericResultImplType> ResponseWrapper<T> get(String url, Type responseClazzType) {
        ClientResponse response = null;
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<>();
        try {
            log.debug("request url as :GET {}", url);
            response = client.get(url);
            processResponse(url, response, responseClazzType, responseWrapper);

        } catch (Exception e) {
            log.error("Exception happened during calling get rest call ", e);
            responseWrapper.setException(e);
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
    public <T> ResponseWrapper<T> list(String url, Type responseClazzType) {
        ClientResponse response = null;
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<>();
        try {
            log.debug("request url as :LIST {}", url);
            response = client.get(url);
            processIteratorResponse(url, response, responseClazzType, responseWrapper);

        } catch (Exception e) {
            log.error("Exception happened during calling get rest call {}", e);
            responseWrapper.setException(e);
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
    public <T extends GenericResultImplType> ResponseWrapper<T> post(String endPoint, ParamType param, Type responseClazzType) {
        ClientResponse response = null;
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<>();
        try {
            log.debug("Request url as :POST {}", endPoint);
            log.debug("Request body as :{}", param.toJsonString());
            response = client.post(endPoint, param.toJsonString());
            processResponse(endPoint, response, responseClazzType, responseWrapper);

        } catch (Exception e) {
            log.error("Exception happened during calling post rest call {}", e);
            responseWrapper.setException(e);
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
    public <T extends GenericResultImplType> ResponseWrapper<T> put(String url, ParamType params, Type responseClazzType) {
        ClientResponse response = null;
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<>();
        try {
            log.debug("Request url as :PUT {}", url);
            log.debug("Request body as :{}", params.toJsonString());
            response = client.put(url, params.toJsonString());
            processResponse(url, response, responseClazzType, responseWrapper);

        } catch (Exception e) {
            log.error("Exception happened during calling put rest call {}", e);
            responseWrapper.setException(e);
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
    public <T extends GenericResultImplType> ResponseWrapper<T> delete(String url, Type responseClazzType) {
        ClientResponse response = null;
        ResponseWrapper<T> responseWrapper = new ResponseWrapper<>();
        try {
            log.debug("request url as :DELETE {}", url);
            response = client.delete(url);
            processResponse(url, response, responseClazzType, responseWrapper);

        } catch (Exception e) {
            log.error("Exception happened during calling delete rest call {}", e);
            responseWrapper.setException(e);
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
     * @throws FailedGetResourceException
     */
    public <T extends GenericResultImplType> ResultListType<T> getNextPageForIterator(String iteratorId, int from, int to,
            Type responseClazzType) throws FailedGetResourceException {

        Map<String, String> filters = new HashMap<>();
        filters.put(FROM_KEY, String.valueOf(from));
        filters.put(TO_KEY, String.valueOf(to));
        String url = UrlGenerator.genUrl(EndPoint.Common.LIST_RESOURCE, UrlGenerator.genUrlFillers(iteratorId), filters);

        @SuppressWarnings("rawtypes")
        ResponseWrapper<ResultListType> responseWrapper = get(url, responseClazzType);
        @SuppressWarnings("unchecked")
        ResultListType<T> responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            log.error("Exception happened during listing resources:{}", responseWrapper.getException());
            throw new FailedGetResourceException(String.format("Exception happened during listing resources:%s",
                    responseWrapper.getException()));
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
     * @throws FailedGetResourceException
     */
    public <T extends GenericResultImplType> List<T> getLeftAllResourcesOfIterator(IteratorType<T> iterator, Type responseClassType)
            throws FailedGetResourceException {
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
     * @throws FailedGetResourceException
     */
    public <T extends GenericResultImplType> List<T> getAllResourcesOfItatrator(IteratorType<T> iterator, Type responseClassType)
            throws FailedGetResourceException {
        List<T> resourceList = new ArrayList<>();
        resourceList.addAll(iterator.fetchAllResults());
        resourceList.addAll(getLeftAllResourcesOfIterator(iterator, responseClassType));

        return resourceList;
    }

    public void appendExceptionMessage(GenericResultImplType responseBean, String template, Object... params) {
        responseBean.setHttpCode(SymConstants.StatusCode.EXCEPTION);
        responseBean.setMessage(String.format(template, params));
    }

    private void closeResponse(ClientResponse response) {
        if (response != null) {
            response.close();
        }
    }

    private String genErrorMessageFromStatusAndMessage(int status, String message) {
        return String.format("%d:%s:%s", status, ClientResponse.Status.fromStatusCode(status).toString(), message);
    }

    private <T extends GenericResultImplType> void processResponse(String url, ClientResponse response, Type responseClazzType,
            ResponseWrapper<T> responseWrapper) throws JSONException {
        log.debug("response as :{}", response);
        if (response == null) {
            responseWrapper.setException(new NullResponseException(String.format("Null Response meet during calling %s", url)));
            return;
        }

        int status = response.getStatus();

        JSONObject responseJson = processHttpCode(responseWrapper, response);
        if (responseWrapper.getException() != null) {
            return;
        }

        T bean = JsonUtil.fromJson((responseJson.toString()), responseClazzType);
        bean.setHttpCode(status);
        responseWrapper.setResponseBean(bean);
    }

    /**
     * @param responseWrapper
     * @param status
     */
    private <T> JSONObject processHttpCode(ResponseWrapper<T> responseWrapper, ClientResponse response) {
        int status = response.getStatus();
        String responseBody = "";
        String errorMsg = "";
        JSONObject jsonResponse = new JSONObject();
        if (ClientResponse.Status.NO_CONTENT.getStatusCode() != status) {

            try {
                responseBody = response.getEntity(String.class);
                jsonResponse = new JSONObject(responseBody);
                GenericResultImplType bean = JsonUtil.fromJson((jsonResponse.toString()), GenericResultImplType.class);
                errorMsg = bean.getMessage();
            } catch (Exception e) {
                errorMsg = responseBody;
            }
        }
        log.debug("Response code as : {}", status);
        log.debug("Response body as : {}", jsonResponse.toString());
        log.debug("Message as : {}", errorMsg);
        if (isClientFamilyError(status)) {
            // 4??
            responseWrapper.setException(new ClientException(genErrorMessageFromStatusAndMessage(status, errorMsg)));
        }
        if (isServerFamilyError(status)) {
            // 5??
            responseWrapper.setException(new ServerException(genErrorMessageFromStatusAndMessage(status, errorMsg)));
        }
        if (isNoResourceFoundError(status)) {
            // 404
            responseWrapper.setException(new NoResourceFoundException(genErrorMessageFromStatusAndMessage(status, errorMsg)));
        }
        return jsonResponse;
    }

    private boolean isNoResourceFoundError(int status) {
        return Response.Status.NOT_FOUND.getStatusCode() == status;
    }

    /**
     * @param status
     * @return
     */
    private boolean isServerFamilyError(int status) {
        return ClientResponse.Status.fromStatusCode(status).getFamily() == Response.Status.Family.SERVER_ERROR;
    }

    /**
     * @param status
     * @return
     */
    private boolean isClientFamilyError(int status) {
        return ClientResponse.Status.fromStatusCode(status).getFamily() == Response.Status.Family.CLIENT_ERROR;
    }

    private <T> void processIteratorResponse(String url, ClientResponse response, Type responseClazzType,
            ResponseWrapper<T> responseWrapper) {
        log.debug("response as :{}", response);
        if (response == null) {
            responseWrapper.setException(new NullResponseException(String.format("Null Response meet during calling %s", url)));
            return;
        }

        JSONObject responseJson = processHttpCode(responseWrapper, response);
        if (responseWrapper.getException() != null) {
            return;
        }

        log.debug("Jsonobject as : {}", responseJson);
        IteratorType<T> beanIterator = JsonUtil.fromJson(responseJson.toString(), responseClazzType);
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
