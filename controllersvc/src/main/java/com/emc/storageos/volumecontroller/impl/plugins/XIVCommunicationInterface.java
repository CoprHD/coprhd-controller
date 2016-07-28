/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import java.net.URI;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;
import com.emc.storageos.services.restutil.RestClientFactory;
import com.emc.storageos.xiv.api.XIVRestClient;
import com.emc.storageos.xiv.api.XIVRestException;

/**
 * Communication interface for IBM XIV. This is mainly used to validate Management URL - HyperScale Manager URL for its availability
 * 
 */
public class XIVCommunicationInterface extends SMICommunicationInterface {
    
    private static final Logger _logger = LoggerFactory.getLogger(XIVCommunicationInterface.class);
    private RestClientFactory _restClientFactory;
   
    /**
     * Validates the Management URL (Secondary URL) of a StorageProvider
     * @param restURL Secondary URL - Management URL
     * @param username User Name
     * @param password Password
     * @throws Exception If validation fails or if the specified URL is not reachable
     */
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
    public void setRestClientFactory(RestClientFactory restClientFactory) {
        this._restClientFactory = restClientFactory;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void scan(AccessProfile accessProfile) throws BaseCollectionException {
        //Call scan method of SMICommunication interface to validate SMIS connectivity
        super.scan(accessProfile);
        
        URI providerURI = null;
        StorageProvider providerObj = null;
        String detailedStatusMessage = "Unknown Status";
        try {
            providerURI = accessProfile.getSystemId();
            providerObj = _dbClient.queryObject(StorageProvider.class, providerURI);
            
            // Validate Secondary URL for its availability
            validateManagementURL(providerObj.getSecondaryURL(), providerObj.getSecondaryUsername(), providerObj.getSecondaryPassword());
            
            // scan succeeds
            detailedStatusMessage = String.format("Scan job completed successfully for REST API: %s", providerObj.getSecondaryURL());
        } catch (Exception e) {
            detailedStatusMessage = String.format("Scan job failed for REST API: %s because %s", providerObj.getSecondaryURL(), e.getMessage());
            throw new SMIPluginException(detailedStatusMessage);
        } finally {
            if (providerObj != null) {
                try {
                    // set detailed message
                    providerObj.setLastScanStatusMessage(detailedStatusMessage);
                    _dbClient.updateObject(providerObj);
                } catch (DatabaseException ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
        }
    }

}
