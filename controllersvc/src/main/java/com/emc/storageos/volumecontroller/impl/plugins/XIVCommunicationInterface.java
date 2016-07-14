/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import java.net.URI;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.services.restutil.RestClientFactory;
import com.emc.storageos.xiv.api.XIVRestClient;
import com.emc.storageos.xiv.api.XIVRestException;

/**
 * Communication interface for IBM XIV. This is mainly used to validate Management URL - HyperScale Manager URL for its availability
 * 
 */
public class XIVCommunicationInterface extends SMICommunicationInterface {
    
    private RestClientFactory _restClientFactory;
   
    /*
     * (non-Javadoc)
     * @see com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface#validateManagementURL(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    protected void validateManagementURL(final String restURL, final String username, final String password) throws Exception{
        if (StringUtils.isNotEmpty(restURL) && StringUtils.isNotEmpty(username) && StringUtils.isNotEmpty(password)) {
            XIVRestClient restClient = (XIVRestClient) _restClientFactory.getRESTClient(URI.create(restURL), username, password);
            if(null!=restClient){
                if(!restClient.getSystemsAvailability()){
                    throw XIVRestException.exceptions.xivRestRequestFailure(restURL, "Hyperscale Manager is not configured.");
                }
            }
        }
    }

    /**
     * Setter for Rest Client Factory
     * @param _restClientFactory Factory instance
     */
    public void setRestClientFactory(RestClientFactory _restClientFactory) {
        this._restClientFactory = _restClientFactory;
    }

}
