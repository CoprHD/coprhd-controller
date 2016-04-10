package com.emc.storageos.driver.driversimulator;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.jsoup.helper.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.ObjectNamespace;
import com.emc.storageos.services.util.SecurityUtils;
import com.google.gson.Gson;
import com.sun.jersey.api.client.ClientResponse;

public class ONEApi {
    private Logger _log = LoggerFactory.getLogger(ONEApi.class);
    private final URI _baseUrl;
    private final RESTClient _client;
    private String authToken;
    
    private static final URI URI_LOGIN = URI.create("/api/v1/credentials");

    public ONEApi(URI endpoint, RESTClient client) {
        _baseUrl = endpoint;
        _client = client;
    }

    /**
     * Close client resources
     */
    public void close() {
        _client.close();
    }
    
    public String getAuthToken() throws ONEException {
        _log.info("ONEApi:getAuthToken enter");
        List<String> authTokenList = null;
        ClientResponse clientResp = null;
        String body= "{\"user\":\"superme\", \"password\":\"superme\"}";

        clientResp = _client.post_json(_baseUrl.resolve(URI_LOGIN), body);
        if (clientResp.getStatus() != 201) {
            _log.info("11111111");
            return null;
        }

        String responseString = clientResp.getEntity(String.class);
        
//        MultivaluedMap<String, String> headers = clientResp.getHeaders();
//        authTokenList = headers.get("key");
//        if (authTokenList.size() != 1) {
//        	_log.info("22222");
//        	return null;
//        }
//        authToken = authTokenList.get(0);
//        _log.info("ONEApi:getAuthToken leave");
        return responseString;
    }
    
	
}
