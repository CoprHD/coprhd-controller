/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.impl.jersey.ClientUtils.addQueryParam;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_CREATE_APP_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_DELETE_APP_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_UPDATE_APP_URL;
import java.net.URI;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.application.ApplicationCreateParam;
import com.emc.storageos.model.application.ApplicationList;
import com.emc.storageos.model.application.ApplicationRestRep;
import com.emc.storageos.model.application.ApplicationUpdateParam;
import com.emc.vipr.client.impl.RestClient;


public class ApplicationSupport {
    protected final RestClient client;
    
    public ApplicationSupport(RestClient client) {
        this.client = client;
    }
    
    /**
     * Creates an application.
     * <p>
     * API Call: POST /applications/block
     * 
     * @return The new state of the cluster
     */
    public ApplicationRestRep createApplication(ApplicationCreateParam input) {
        return client.post(ApplicationRestRep.class, input, APP_SUPPORT_CREATE_APP_URL);
    }
    
    /**
     * Get List of applications
     * API call: GET /applications/block
     * @return List of applications
     */
    
    public ApplicationList getApplications() {
        return client.get(ApplicationList.class, APP_SUPPORT_CREATE_APP_URL, "");
    }
    
    /**
     * Deletes an application
     * API Call: POST /applications/block/{id}/deactivate
     * 
     */
    public void deleteApplication(URI id) {
        client.post(String.class, APP_SUPPORT_DELETE_APP_URL, id);
    }
    
    /**
     * Update an application
     * API call: PUT /applications/block/{id}
     * 
     */
    public ApplicationRestRep updateApplication(ApplicationUpdateParam input, URI id) {
        return client.put(ApplicationRestRep.class, APP_SUPPORT_UPDATE_APP_URL, id);
    }
    
    /**
     * Get application based on ID
     * 
     */
    public ApplicationRestRep getApplication(URI id) {
        return client.getURI(ApplicationRestRep.class, id);
    }
}