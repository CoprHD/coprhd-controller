/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package storageapi;

import java.util.concurrent.ExecutionException;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.libs.F;
import play.libs.WS;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ning.http.util.UTF8UrlEncoder;
import static com.emc.vipr.client.impl.Constants.AUTH_TOKEN_KEY;

/**
 * JSON WS API wrapper for accessing the backed storage APIs. This wrapper adds parsing to the JSON, basic logging and
 * the auth token support.
 * 
 * @author Jonny Miller
 * @author Chris Dail
 */
public class JsonAPI {
    public static final String TIMEOUT = "10min";

    protected ApiUrlFactory apiUrlFactory;
    protected Gson gson;

    public JsonAPI(ApiUrlFactory apiUrlFactory) {
        this.apiUrlFactory = apiUrlFactory;
        this.gson = new GsonBuilder().serializeNulls().create();
    }

    // POST with no payload
    protected <T> F.Promise<APIResponse<T>> post(String authToken, Class<T> responseType, String path, String... args) {
        return post(authToken, null, responseType, path, args);
    }

    // Standard POST
    protected <T> F.Promise<APIResponse<T>>
            post(String authToken, Object request, final Class<T> responseType, String path, String... args) {
        WS.WSRequest wsReq = newRequest(authToken, request, path, args);
        Logger.info("POST: %s, %s", wsReq.url, wsReq.body);
        return responseHandler(wsReq.postAsync(), responseType);
    }

    // POST with no response
    protected F.Promise<APIResponse<Boolean>> post(String authToken, Object request, String path, String... args) {
        WS.WSRequest wsReq = newRequest(authToken, request, path, args);
        Logger.info("POST: %s, %s", wsReq.url, wsReq.body);
        return responseHandler(wsReq.postAsync());
    }

    // DELETE with no payload
    protected F.Promise<APIResponse<Boolean>> delete(String authToken, String path, String... args) {
        WS.WSRequest wsReq = newRequest(authToken, null, path, args);
        Logger.info("DELETE: %s, %s", wsReq.url, wsReq.body);
        return responseHandler(wsReq.deleteAsync());
    }

    // PUT with no payload
    protected F.Promise<APIResponse<Boolean>> put(String authToken, String path, String... args) {
        WS.WSRequest wsReq = newRequest(authToken, null, path, args);
        Logger.info("PUT: %s, %s", wsReq.url, wsReq.body);
        return responseHandler(wsReq.putAsync());
    }

    // PUT with payload
    protected <T> F.Promise<APIResponse<T>> put(String authToken, Object request, final Class<T> responseType, String path, String... args) {
        WS.WSRequest wsReq = newRequest(authToken, request, path, args);
        Logger.info("PUT: %s, %s", wsReq.url, wsReq.body);
        return responseHandler(wsReq.putAsync(), responseType);
    }

    // GET
    protected <T> F.Promise<APIResponse<T>> get(String authToken, final Class<T> responseType, String path, String... args) {
        WS.WSRequest wsReq = newRequest(authToken, null, path, args);
        Logger.info("GET: %s, %s", wsReq.url, wsReq.body);
        return responseHandler(wsReq.getAsync(), responseType);
    }

    protected <T> APIResponse<T> readJson(WS.HttpResponse response, Class<T> type) {
        if (response.success()) {
            return new APIResponse<T>(response.getStatus(), gson.fromJson(response.getJson(), type));
        }

        if (StringUtils.isBlank(response.getStatusText())) {
            return new APIResponse<T>(response.getStatus(), null);
        }
        else {
            return new APIResponse<T>(new APIException(response.getStatus(), response.getStatusText(), response.getString()));
        }
    }

    // The default play encode() function uses the Java URLEncoder. This is wrong. Query parameters must be encoded
    // using RFC-3986 which is not the same.
    protected String encodeUrl(String template, String... params) {
        Object[] encodedParams = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            encodedParams[i] = UTF8UrlEncoder.encode(params[i]);
        }
        return String.format(template, encodedParams);
    }

    /**
     * Creates a new WSRequest to the API using the given path. the request is added parsed as JSON.
     * 
     * @param authToken Auth token sent as the Security.AUTH_HEADER
     * @param path Path to the API
     * @param request Request object (can be null)
     * @return WS.WSRequest
     */
    protected WS.WSRequest newRequest(String authToken, Object request, String path, String... args) {
        WS.WSRequest wsReq = WS.url(encodeUrl(apiUrlFactory.getUrl() + path, args));
        wsReq.timeout(TIMEOUT);

        if (authToken != null) {
            wsReq.setHeader(AUTH_TOKEN_KEY, authToken);
        }
        wsReq.setHeader("Accept", "application/json");
        if (request != null) {
            String json = gson.toJson(request);
            wsReq.setHeader("Content-Type", "application/json");
            wsReq.body(json);
        }
        return wsReq;
    }

    /**
     * Creates a wrapper around a promise result of the WS request. All this does is log the output.
     */
    protected F.Promise<APIResponse<Boolean>> responseHandler(F.Promise<WS.HttpResponse> wsRespPromise) {
        final F.Promise<APIResponse<Boolean>> methodResult = new F.Promise<APIResponse<Boolean>>();
        wsRespPromise.onRedeem(new F.Action<F.Promise<WS.HttpResponse>>() {
            @Override
            public void invoke(F.Promise<WS.HttpResponse> result) {
                try {
                    WS.HttpResponse wsResp = result.get();
                    Logger.info("API RESP: %d, %s", wsResp.getStatus(), wsResp.getString());
                    methodResult.invoke(new APIResponse(wsResp.getStatus(), wsResp.success()));
                }
                catch (ExecutionException e) {
                    Logger.error(e, "API Exception");
                    methodResult.invoke(new APIResponse(e.getCause()));
                }
                catch (InterruptedException e) {
                    Logger.error(e, "API Exception");
                    methodResult.invoke(new APIResponse(e));
                }
                catch (RuntimeException e) {
                    Logger.error(e, "API Exception");
                    methodResult.invoke(new APIResponse(e));
                }
            }
        });
        return methodResult;
    }

    /**
     * Wraps a promise response with a new promise. This one parses the result as JSON returning the JSON to the new
     * promise.
     * 
     * @param wsRespPromise WS Response
     * @param responseType Class to parse JSON
     * @param <T> Type of the request
     * @return A new promise that will return the parsed JSON
     */
    protected <T> F.Promise<APIResponse<T>> responseHandler(F.Promise<WS.HttpResponse> wsRespPromise, final Class<T> responseType) {
        final F.Promise<APIResponse<T>> methodResult = new F.Promise<APIResponse<T>>();
        wsRespPromise.onRedeem(new F.Action<F.Promise<WS.HttpResponse>>() {
            @Override
            public void invoke(F.Promise<WS.HttpResponse> result) {
                try {
                    WS.HttpResponse wsResp = result.get();
                    Logger.info("API RESP: %d, %s", wsResp.getStatus(), wsResp.getString());
                    methodResult.invoke(readJson(wsResp, responseType));
                }
                catch (ExecutionException e) {
                    handleAPIException(e.getCause());
                }
                catch (InterruptedException e) {
                    handleAPIException(e);
                }
                catch (APIException e) {
                    handleAPIException(e);
                }
                // GSON exceptions are runtime exceptions
                catch (RuntimeException e) {
                    handleAPIException(e);
                }
                catch (Exception e) {
                    handleAPIException(e);
                }
            }

            private void handleAPIException(Throwable e) {
                Logger.error(e, "API Exception");
                methodResult.invoke(new APIResponse<T>(e));
            }
        });
        return methodResult;
    }

}
