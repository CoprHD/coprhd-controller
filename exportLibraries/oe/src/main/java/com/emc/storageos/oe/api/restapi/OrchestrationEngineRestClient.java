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

package com.emc.storageos.oe.api.restapi;

import java.net.URI;

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

public class OrchestrationEngineRestClient extends StandardRestClient {

    private static Logger log = LoggerFactory.getLogger(OrchestrationEngineRestClient.class);

    /**
     * Constructor
     * 
     * @param factory A reference to the OrchestrationRestClientFactory
     * @param baseURI the base URI to connect to OE Gateway
     * @param client A reference to a Jersey Apache HTTP client.
     */
    public OrchestrationEngineRestClient(URI baseURI, Client client) {
        _client = client;
        _base = baseURI;
    }

    @Override
    protected Builder setResourceHeaders(WebResource resource) {
        return resource.getRequestBuilder();
    }

    @Override
    protected void authenticate() {
        // this is a generic rest client.  authentication done manually
    }

    @Override
    protected int checkResponse(URI uri, ClientResponse response) { 
        ClientResponse.Status status = response.getClientResponseStatus();
        int errorCode = status.getStatusCode(); 
        if (errorCode >= 300) {
            String entity;
            try {
                entity = response.getEntity(String.class);
            } catch (Exception e) {   
                log.error("Parsing the failure response object failed.  " +
                        e.getMessage() ,e);
                entity = "(could not parse response)";
            }
            if (errorCode == 404 || errorCode == 410) {
                log.warn("OE Request resource not found for " + uri.toString());
            } else if (errorCode == 401) {
                log.warn("OE Request authentication failed for " + uri.toString());
            } else {
                log.warn("OE Request Internal Error for " + uri.toString() + "returned " + entity);
            }
        } 
        return errorCode;
    }
}
