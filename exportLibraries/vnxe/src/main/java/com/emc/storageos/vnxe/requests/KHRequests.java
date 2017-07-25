/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.requests;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.vnxe.VNXeConstants;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.models.ParamBase;
import com.emc.storageos.vnxe.models.VNXeCommandJob;
import com.emc.storageos.vnxe.models.VNXeCommandResult;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.ClientResponse.Status;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;

/*
 * This is the base class for request sending to KittyHawk/VNXe server
 */
public class KHRequests<T> {
    private static final Logger _logger = LoggerFactory.getLogger(KHRequests.class);
    protected MultivaluedMap<String, String> _queryParams;
    protected String _url;
    protected String _fields;
    protected KHClient _client;
    private WebResource _resource;
    private Set<NewCookie> _requestCookies = new HashSet<NewCookie>();

    private static final String AUTH_TOKEN = "Cookie";
    private static final String CLIENT_HEADER = "X-EMC-REST-CLIENT";
    private static final String EMC_CSRF_HEADER = "EMC-CSRF-TOKEN";
    private static final String GET_REQUEST = "GET";
    private static final String POST_REQUEST = "POST";
    private static final String DELETE_REQUEST = "DELETE";
    private static final String CONNECTION = "Connection";
    private static final String CLOSE = "close";

    public KHRequests(KHClient client) {
        _client = client;
        _resource = client.getResource();
    }

    protected KHClient getClient() {
        return _client;
    }

    public void setQueryParameters(MultivaluedMap<String, String> queryParams) {
        if (_queryParams != null) {
            for (String key : queryParams.keySet()) {
                List<String> values = queryParams.get(key);
                for (String value : values) {
                    _queryParams.add(key, value);
                }
            }
        } else {
            _queryParams = queryParams;
        }
    }

    public void unsetQueryParameters() {
        _queryParams = null;
    }

    public WebResource buildResource(WebResource resource) {
        return resource.path(_url);
    }

    /*
     * Build the request with headers and cookies
     */
    protected WebResource.Builder buildRequest(WebResource.Builder builder) {

        builder = builder.header(CLIENT_HEADER, "true");
        if (_client.getEmcCsrfToken() != null) {
            _logger.debug("EMC-CSRF-TOKEN is:: " + _client.getEmcCsrfToken());
            builder.header(EMC_CSRF_HEADER, _client.getEmcCsrfToken());
        }
        builder.header(CONNECTION, CLOSE);
        Set<NewCookie> cookies = null;
        if (!_requestCookies.isEmpty()) {
            cookies = _requestCookies;
        } else {
            cookies = _client.getCookie();
        }
        if (cookies != null && !cookies.isEmpty()) {

            StringBuilder buildCookies = new StringBuilder();
            int n = 0;
            for (NewCookie cookie : cookies) {
                if (n == 0) {
                    buildCookies.append(cookie.toString());
                } else {
                    buildCookies.append(";");
                    buildCookies.append(cookie.toString());
                }
                n++;
            }
            _logger.debug("setting the cookie:" + buildCookies.toString());
            builder = builder.header(AUTH_TOKEN, buildCookies.toString());

        }

        builder = builder.accept(MediaType.APPLICATION_JSON_TYPE);
        builder = builder.type(MediaType.APPLICATION_JSON_TYPE);
        return builder;
    }

    protected WebResource addQueryParameters(WebResource resource) {
        if (_queryParams == null) {
            return resource; // no query parameters
        }
        _logger.debug("_queryParams:" + _queryParams);
        return resource.queryParams(_queryParams);
    }

    /*
     * get a list type of request
     * e.g. GET /api/types/system/instances
     * 
     * @param resource WebResource
     * 
     * @param valueType class type
     * 
     * @return list of objects
     * 
     * @throws VnxeException unexpectedDataError
     */
    public List<T> getDataForObjects(Class<T> valueType)
            throws VNXeException {
        _logger.info("getting data: {}", _url);
        ClientResponse response = sendGetRequest(_resource);
        String emcCsrfToken = response.getHeaders().getFirst(EMC_CSRF_HEADER);
        if (emcCsrfToken != null) {
            saveEmcCsrfToken(emcCsrfToken);
        }

        saveClientCookies();
        String resString = response.getEntity(String.class);
        _logger.info("got data: " + resString);
        JSONObject res;
        List<T> returnedObjects = new ArrayList<T>();
        try {
            res = new JSONObject(resString);
            if (res != null) {
                JSONArray entries = res.getJSONArray(VNXeConstants.ENTRIES);
                if (entries != null && entries.length() > 0) {
                    for (int i = 0; i < entries.length(); i++) {
                        JSONObject entry = entries.getJSONObject(i);
                        JSONObject object = (JSONObject) entry.get(VNXeConstants.CONTENT);
                        if (object != null) {
                            String objectString = object.toString();
                            ObjectMapper mapper = new ObjectMapper();
                            try {
                                T returnedObject = mapper.readValue(objectString, valueType);
                                returnedObjects.add(returnedObject);
                            } catch (JsonParseException e) {
                                _logger.error(String.format("unexpected data returned: %s from: %s", objectString, _url), e);
                                throw VNXeException.exceptions.unexpectedDataError(objectString, e);

                            } catch (JsonMappingException e) {
                                _logger.error(String.format("unexpected data returned: %s from: %s", objectString, _url), e);
                                throw VNXeException.exceptions.unexpectedDataError(objectString, e);
                            } catch (IOException e) {
                                _logger.error(String.format("unexpected data returned: %s from: %s", objectString, _url), e);
                                throw VNXeException.exceptions.unexpectedDataError(objectString, e);
                            }

                        }
                    }
                }
            }
        } catch (JSONException e) {
            _logger.error(String.format("unexpected data returned: %s from: %s", resString, _url), e);
            throw VNXeException.exceptions.unexpectedDataError(resString, e);
        } 
        return returnedObjects;
    }

    /*
     * get one instance request
     * e.g. GET /api/instances/nasServer/<id>
     * 
     * @param resource WebResource
     * 
     * @param Class<T> class type
     * 
     * @throws VnexException unexpectedDataError
     */
    public T getDataForOneObject(Class<T> valueType) throws VNXeException {
        _logger.debug("getting data: " + _url);
        ClientResponse response = sendGetRequest(_resource);
        String emcCsrfToken = response.getHeaders().getFirst(EMC_CSRF_HEADER);
        if (emcCsrfToken != null) {
            saveEmcCsrfToken(emcCsrfToken);
        }

        saveClientCookies();
        String resString = response.getEntity(String.class);
        _logger.debug("got data: " + resString);
        JSONObject res;
        String objectString = null;
        T returnedObject = null;
        try {
            res = new JSONObject(resString);
            if (res != null) {
                JSONObject object = (JSONObject) res.get(VNXeConstants.CONTENT);
                if (object != null) {
                    objectString = object.toString();
                    ObjectMapper mapper = new ObjectMapper();
                    try {
                        returnedObject = mapper.readValue(objectString, valueType);
                    } catch (JsonParseException e) {
                        _logger.error(String.format("unexpected data returned: %s from: %s", objectString, _url), e);
                        throw VNXeException.exceptions.unexpectedDataError("unexpected data returned:" + objectString, e);
                    } catch (JsonMappingException e) {
                        _logger.error(String.format("unexpected data returned: %s from: %s", objectString, _url), e);
                        throw VNXeException.exceptions.unexpectedDataError("unexpected data returned:" + objectString, e);
                    } catch (IOException e) {
                        _logger.error(String.format("unexpected data returned: %s from: %s", objectString, _url), e);
                        throw VNXeException.exceptions.unexpectedDataError("unexpected data returned:" + objectString, e);
                    }

                }
            }
        } catch (JSONException e) {
            _logger.error(String.format("unexpected data returned: %s from: %s", resString, _url), e);
            throw VNXeException.exceptions.unexpectedDataError("unexpected data returned:" + objectString, e);
        } 
        return returnedObject;
    }

    /*
     * Send POST request to KittyHawk server, and handle redirect/cookies
     * 
     * @param resource webResource
     * 
     * @param ParamBase parameters for post
     * 
     * @throws VNXeException
     */
    public ClientResponse postRequest(ParamBase param) throws VNXeException {
        _logger.debug("post data: " + _url);
        ObjectMapper mapper = new ObjectMapper();
        String parmString = null;
        if (param != null) {
            try {
                parmString = mapper.writeValueAsString(param);
                _logger.debug("Content of the post: {}", parmString);
            } catch (JsonGenerationException e) {
                _logger.error("Post request param is not valid. ", e);
                throw VNXeException.exceptions.vnxeCommandFailed("Post request param is not valid.", e);
            } catch (JsonMappingException e) {
                _logger.error("Post request param is not valid. ", e);
                throw VNXeException.exceptions.vnxeCommandFailed("Post request param is not valid.", e);
            } catch (IOException e) {
                _logger.error("Post request param is not valid. ", e);
                throw VNXeException.exceptions.vnxeCommandFailed("Post request param is not valid.", e);
            }
        }
        ClientResponse response = buildRequest(addQueryParameters(buildResource(_resource))
                .getRequestBuilder()).entity(parmString).post(ClientResponse.class);
        Status statusCode = response.getClientResponseStatus();
        if (statusCode == ClientResponse.Status.CREATED
                || statusCode == ClientResponse.Status.ACCEPTED
                || statusCode == ClientResponse.Status.OK
                || statusCode == ClientResponse.Status.NO_CONTENT) {
            return response;
        } else if (statusCode == ClientResponse.Status.UNAUTHORIZED) {
            authenticate();
            response = buildRequest(addQueryParameters(buildResource(_resource))
                    .getRequestBuilder()).entity(parmString).post(ClientResponse.class);
            ;
            statusCode = response.getClientResponseStatus();

            if (statusCode == ClientResponse.Status.OK
                    || statusCode == ClientResponse.Status.ACCEPTED
                    || statusCode == ClientResponse.Status.NO_CONTENT
                    || statusCode == ClientResponse.Status.CREATED) {
                return response;
            }
        }
        int redirectTimes = 1;
        // handle redirect
        while (response.getClientResponseStatus() == ClientResponse.Status.FOUND &&
                redirectTimes < VNXeConstants.REDIRECT_MAX) {

            String code = response.getClientResponseStatus().toString();
            String data = response.getEntity(String.class);
            _logger.debug("Returned code: {}, returned data {}", code, data);
            WebResource newResource = handelRedirect(response);
            if (newResource != null) {
                response = buildRequest(newResource.getRequestBuilder()).entity(parmString).post(ClientResponse.class);
                redirectTimes++;
            } else {
                // could not find the redirect url, return
                _logger.error(String.format("The post request to: %s failed with: %s %s", _url,
                        response.getClientResponseStatus().toString(), response.getEntity(String.class)));
                throw VNXeException.exceptions.unexpectedDataError("Got redirect status code, but could not get redirected URL");
            }
        }
        if (redirectTimes >= VNXeConstants.REDIRECT_MAX) {
            _logger.error("redirected too many times for the request {}", _url);
            throw VNXeException.exceptions.unexpectedDataError("Redirected too many times while sending the request for " + _url);
        }
        checkResponse(response, POST_REQUEST);
        return response;

    }

    public VNXeCommandJob postRequestAsync(ParamBase param) {
        setAsyncMode();
        ClientResponse response = postRequest(param);

        VNXeCommandJob job;
        String resString = response.getEntity(String.class);
        ObjectMapper mapper = new ObjectMapper();
        try {
            job = mapper.readValue(resString, VNXeCommandJob.class);
        } catch (JsonParseException e) {
            _logger.error(String.format("unexpected data returned: %s from: %s", resString, _url), e);
            throw VNXeException.exceptions.unexpectedDataError("unexpected data returned:" + resString, e);
        } catch (JsonMappingException e) {
            _logger.error(String.format("unexpected data returned: %s from: %s", resString, _url), e);
            throw VNXeException.exceptions.unexpectedDataError("unexpected data returned:" + resString, e);
        } catch (IOException e) {
            _logger.error(String.format("unexpected data returned: %s from: %s", resString, _url), e);
            throw VNXeException.exceptions.unexpectedDataError("unexpected data returned:" + resString, e);
        } 
        if (job != null) {
            _logger.info("submitted the job: " + job.getId());
        } else {
            _logger.warn("No job returned.");
        }
        return job;
    }

    public VNXeCommandResult postRequestSync(ParamBase param) {
        ClientResponse response = postRequest(param);
        if (response.getClientResponseStatus() == ClientResponse.Status.NO_CONTENT) {
            VNXeCommandResult result = new VNXeCommandResult();
            result.setSuccess(true);
            return result;
        }
        String resString = response.getEntity(String.class);
        _logger.debug("KH API returned: {} ", resString);
        JSONObject res;
        String objectString = null;
        VNXeCommandResult returnedObject = null;
        try {
            res = new JSONObject(resString);
            if (res != null) {
                JSONObject object = (JSONObject) res.get(VNXeConstants.CONTENT);
                if (object != null) {
                    objectString = object.toString();
                    ObjectMapper mapper = new ObjectMapper();
                    try {
                        returnedObject = mapper.readValue(objectString, VNXeCommandResult.class);
                        returnedObject.setSuccess(true);
                    } catch (JsonParseException e) {
                        _logger.error(String.format("unexpected data returned: %s", objectString), e);
                        throw VNXeException.exceptions.unexpectedDataError(String.format("unexpected data returned: %s", objectString), e);
                    } catch (JsonMappingException e) {
                        _logger.error(String.format("unexpected data returned: %s", objectString), e);
                        throw VNXeException.exceptions.unexpectedDataError(String.format("unexpected data returned: %s", objectString), e);
                    } catch (IOException e) {
                        _logger.error(String.format("unexpected data returned: %s", objectString), e);
                        throw VNXeException.exceptions.unexpectedDataError(String.format("unexpected data returned: %s", objectString), e);
                    }

                }
            }
        } catch (JSONException e) {
            _logger.error(String.format("unexpected data returned: %s from: %s", resString, _url), e);
            throw VNXeException.exceptions.unexpectedDataError(String.format("unexpected data returned: %s", objectString), e);
        } 
        return returnedObject;
    }

    /*
     * Send GET request to KittyHawk server, and handle redirect/cookies
     */
    private ClientResponse sendGetRequest(WebResource resource) throws VNXeException {
        _logger.info("getting data: {} ", _url);
        if (_client.isUnity() == true) {
            setFields();
        }
        ClientResponse response = buildRequest(addQueryParameters(buildResource(resource))
                .getRequestBuilder()).get(ClientResponse.class);
        Status statusCode = response.getClientResponseStatus();
        _logger.info(response.getStatus() + ":" + response.toString());
        if (statusCode == ClientResponse.Status.OK) {
            String emcCsrfToken = response.getHeaders().getFirst(EMC_CSRF_HEADER);
            if (emcCsrfToken != null) {
                saveEmcCsrfToken(emcCsrfToken);
            }

            saveClientCookies();
            return response;
        } else if (response.getClientResponseStatus() == ClientResponse.Status.UNAUTHORIZED) {
            authenticate();
            response = buildRequest(addQueryParameters(buildResource(resource))
                    .getRequestBuilder()).get(ClientResponse.class);
            ;
        }
        int redirectTimes = 1;
        while (response.getClientResponseStatus() == ClientResponse.Status.FOUND &&
                redirectTimes < VNXeConstants.REDIRECT_MAX) {

            String code = response.getClientResponseStatus().toString();
            String data = response.getEntity(String.class);
            _logger.debug("Returned code: {}, returned data:", code, data);
            WebResource newResource = handelRedirect(response);
            if (newResource != null) {
                response = buildRequest(newResource.getRequestBuilder()).get(ClientResponse.class);
                redirectTimes++;
            } else {
                // could not find the redirect url, return
                _logger.error(String.format("The post request to: %s failed with: %s %s", _url,
                        response.getClientResponseStatus().toString(), response.getEntity(String.class)));
                throw VNXeException.exceptions.unexpectedDataError("Got redirect status code, but could not get redirected URL");
            }
        }
        if (redirectTimes >= VNXeConstants.REDIRECT_MAX) {
            _logger.error("redirected too many times for the request {}", _url);
            throw VNXeException.exceptions.unexpectedDataError("Redirected too many times while sending the request for " + _url);
        }

        checkResponse(response, GET_REQUEST);

        List<NewCookie> cookies = response.getCookies();
        if (cookies != null && !cookies.isEmpty()) {
            _requestCookies.addAll(cookies);
        }
        saveClientCookies();

        String emcCsrfToken = response.getHeaders().getFirst(EMC_CSRF_HEADER);
        if (emcCsrfToken != null) {
            saveEmcCsrfToken(emcCsrfToken);
        }
        return response;
    }

    /*
     * Handle request redirects, adding cookies and setting the redirect URL
     */
    private WebResource handelRedirect(ClientResponse response) {
        URI url = response.getLocation();
        List<NewCookie> cookies = response.getCookies();
        if (cookies != null && !cookies.isEmpty()) {
            _requestCookies.addAll(cookies);
        } else {
            _logger.debug("no cookies");
        }
        if (url != null) {
            _logger.debug("redirected url: {}", url.toString());
            WebResource resource = _client.getResource(url.toString());
            return resource;
        } else {
            _logger.error("Could not find redirected url");
            return null;
        }

    }

    /*
     * Send DELETE request to KittyHawk server, and handle redirect/cookies
     * 
     * @param resource webResource
     * 
     * @param param parameters for delete
     * 
     * @throws VNXeException
     */
    public ClientResponse deleteRequest(Object param) throws VNXeException {
        _logger.debug("delete data: " + _url);
        ClientResponse response = sendDeleteRequest(param);

        Status statusCode = response.getClientResponseStatus();
        if (statusCode == ClientResponse.Status.OK
                || statusCode == ClientResponse.Status.ACCEPTED
                || statusCode == ClientResponse.Status.NO_CONTENT) {
            return response;
        } else if (response.getClientResponseStatus() == ClientResponse.Status.UNAUTHORIZED) {
            authenticate();
            response = sendDeleteRequest(param);
            statusCode = response.getClientResponseStatus();
            if (statusCode == ClientResponse.Status.OK
                    || statusCode == ClientResponse.Status.ACCEPTED
                    || statusCode == ClientResponse.Status.NO_CONTENT) {
                return response;
            }
        }
        int redirectTimes = 1;
        // handle redirect
        while (response.getClientResponseStatus() == ClientResponse.Status.FOUND &&
                redirectTimes < VNXeConstants.REDIRECT_MAX) {

            String code = response.getClientResponseStatus().toString();
            String data = response.getEntity(String.class);
            _logger.debug("Returned code: {} returned data: ", code, data);
            WebResource resource = handelRedirect(response);
            if (resource != null) {
                response = buildRequest(resource.getRequestBuilder()).entity(param).delete(ClientResponse.class);
                redirectTimes++;
            } else {
                // could not find the redirect url, return
                _logger.error(String.format("The post request to: %s failed with: %s %s", _url,
                        response.getClientResponseStatus().toString(), response.getEntity(String.class)));
                throw VNXeException.exceptions.unexpectedDataError("Got redirect status code, but could not get redirected URL");
            }
        }
        if (redirectTimes >= VNXeConstants.REDIRECT_MAX) {
            _logger.error("redirected too many times for the request {} ", _url);
            throw VNXeException.exceptions.unexpectedDataError("Redirected too many times while sending the request for " + _url);
        }

        checkResponse(response, DELETE_REQUEST);

        return response;

    }

    /*
     * save cookies in KHClient for next request
     */
    private void saveClientCookies() {
        if (!_requestCookies.isEmpty()) {
            _client.setCookie(_requestCookies);
        }
    }

    /*
     * save EMC_CSRF_TOKEN for next POST or PUT request
     */
    private void saveEmcCsrfToken(String emcCsrfToken) {
        if (emcCsrfToken != null) {
            _logger.debug("Saving CSRF token: " + emcCsrfToken);
            _client.setEmcCsrfToken(emcCsrfToken);
        }
    }

    /*
     * Send DELETE request to KittyHawk server in async mode
     * 
     * @param resource webResource
     * 
     * @param param parameters for delete
     * 
     * @return VNXeCommandJob
     * 
     * @throws VNXeException
     */
    public VNXeCommandJob deleteRequestAsync(Object param) throws VNXeException {
        _logger.debug("delete data: " + _url);
        setAsyncMode();
        ClientResponse response = deleteRequest(param);
        VNXeCommandJob job;
        String resString = response.getEntity(String.class);
        ObjectMapper mapper = new ObjectMapper();
        try {
            job = mapper.readValue(resString, VNXeCommandJob.class);
        } catch (JsonParseException e) {
            _logger.error(String.format("unexpected data returned: %s from: %s ", resString, _url), e);
            throw VNXeException.exceptions.unexpectedDataError(String.format("unexpected data returned: %s", resString), e);
        } catch (JsonMappingException e) {
            _logger.error(String.format("unexpected data returned: %s from: %s ", resString, _url), e);
            throw VNXeException.exceptions.unexpectedDataError(String.format("unexpected data returned: %s", resString), e);
        } catch (IOException e) {
            _logger.error(String.format("unexpected data returned: %s from: %s ", resString, _url), e);
            throw VNXeException.exceptions.unexpectedDataError(String.format("unexpected data returned: %s", resString), e);
        } 
        if (job != null) {
            _logger.info("submitted the deleting file system job: {} ", job.getId());
        }
        return job;

    }

    protected void setAsyncMode() {
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.TIMEOUT, "0");
        setQueryParameters(queryParams);
    }

    protected void setFilter(String filter) {
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.add(VNXeConstants.FILTER, filter);
        setQueryParameters(queryParams);
    }

    protected void setFields() {
        _logger.info("Setting fields:" + _fields);
        if (_fields != null) {
            MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
            queryParams.add(VNXeConstants.FIELDS, _fields);
            setQueryParameters(queryParams);
        }
    }

    private void authenticate() {
        // calling a GET operation would authenticate the client again.
        _client.setCookie(null);
        _client.setEmcCsrfToken(null);
        StorageSystemRequest req = new StorageSystemRequest(_client);
        req.get();
    }

    private ClientResponse sendDeleteRequest(Object param) {
        ClientResponse response = null;

        if (param != null) {
            response = buildRequest(addQueryParameters(buildResource(_resource))
                    .getRequestBuilder()).entity(param).delete(ClientResponse.class);
        } else {
            response = buildRequest(addQueryParameters(buildResource(_resource))
                    .getRequestBuilder()).delete(ClientResponse.class);
        }
        return response;
    }

    private void checkResponse(ClientResponse response, String requestType) {
        Status status = response.getClientResponseStatus();
        if (status != ClientResponse.Status.OK &&
                status != ClientResponse.Status.ACCEPTED &&
                status != ClientResponse.Status.NO_CONTENT) {
            // error
            if (status == ClientResponse.Status.UNAUTHORIZED) {
                throw VNXeException.exceptions.authenticationFailure(_url.toString());
            }
            String code = null;
            code = Integer.toString(response.getStatus());
            StringBuilder errorBuilder = new StringBuilder();
            errorBuilder.append(requestType).append(" request to:");
            errorBuilder.append(_url);
            errorBuilder.append(" failed with status code: ");
            errorBuilder.append(code);
            errorBuilder.append(" ");
            errorBuilder.append("message: ");
            String msg = response.getEntity(String.class);
            errorBuilder.append(msg);
            _logger.error(errorBuilder.toString());

            throw VNXeException.exceptions.vnxeCommandFailed(_url, code, msg);
        }
    }
}
