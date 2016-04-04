package com.emc.storageos.driver.par3driver;

import java.net.URI;
import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.ClientResponse;

public class Par3Api {
	 private Logger _log = LoggerFactory.getLogger(Par3Api.class);
	    private final URI _baseUrl;
	    private final RESTClient _client;
	    private String authToken;
	    
	    private static final URI URI_LOGIN = URI.create("/api/v1/credentials");
	    
	    /**
	     * Constructor for using http connections
	     * 
	     * @throws Par3Exception
	     */
	    public Par3Api(URI endpoint, RESTClient client) {
	        _baseUrl = endpoint;
	        _client = client;
	    }

	    /**
	     * Close client resources
	     */
	    public void close() {
	        _client.close();
	    }
	    
	    public String getAuthToken() throws Par3Exception {
	        _log.info("Par3Api:getAuthToken enter");
	        List<String> authTokenList = null;
	        ClientResponse clientResp = null;
	        
	        String body = "{\"user\":\"superme\", \"password\":\"superme\"}";

	        clientResp = _client.post_json(_baseUrl.resolve(URI_LOGIN), body);
	        if (clientResp.getStatus() != 200) {
	        	_log.info("3par: getAuthToken error");
	            ;//throw Par3Exception.exceptions.unableToConnect(_baseUrl, clientResp.getStatus());
	        }

	        MultivaluedMap<String, String> headers = clientResp.getHeaders();
	        authTokenList = headers.get("key");
	        if (authTokenList.size() != 1) {
	        	_log.info("3par: getAuthToken error222");
	            ;//throw Par3Exception.exceptions.invalidReturnParameters(_baseUrl);
	        }
	        authToken = authTokenList.get(0);
	        _log.info("Par3Api:getAuthToken leave");
	        return authToken;
	    }
	    
}
