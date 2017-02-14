/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.emc.storageos.customservices.api.restapi;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.restutil.StandardRestClient;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;

/**
 * This class implements methods for calling OE REST API to do operations.
 */

public class CustomServicesRestClient extends StandardRestClient {

    private static Logger log = LoggerFactory.getLogger(CustomServicesRestClient.class);
    private Map<String, String> _authHeaders = new HashMap<String, String>();

    /**
     * Constructor
     * 
     * @param factory A reference to the CustomServicesRestClientFactory
     * @param baseURI the base URI to connect to OE Gateway
     * @param client A reference to a Jersey Apache HTTP client.
     */
    public CustomServicesRestClient(URI baseURI, Client client) {
        _client = client;
        _base = baseURI;
    }

    public void setAuthHeaders(String token, String extraHeader) {
        _authHeaders.put(token, extraHeader);
    }

    @Override
    protected Builder setResourceHeaders(WebResource resource) {
        WebResource.Builder builder = resource.getRequestBuilder();
        for (Map.Entry<String, String> entry : _authHeaders.entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
  
        return builder;
    }

    @Override
    protected void authenticate() {
        // this is a generic rest client.  authentication done manually
    }

    @Override
    protected int checkResponse(URI uri, ClientResponse response) { 
        ClientResponse.Status status = response.getClientResponseStatus();
        int errorCode = status.getStatusCode(); 
        log.info("OE REST Request returned " + errorCode + " for "+ uri.toString());
        return errorCode;
    }
}
