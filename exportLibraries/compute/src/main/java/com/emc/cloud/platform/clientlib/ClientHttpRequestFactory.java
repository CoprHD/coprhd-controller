/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.platform.clientlib;

import javax.xml.bind.JAXBContext;

import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.params.HttpProtocolParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import com.emc.cloud.http.common.BaseHttpClientFactory;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;

/**
 * Factory for creation of ClientHttpRequest objects
 * 
 * For now a trivial implementation, but ultimately it will ensure the right
 * credentials are plugged in for Bourne CAS and potentially leverage a pool of
 * clients (per user) for efficiency
 * 
 */
public class ClientHttpRequestFactory implements InitializingBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientHttpRequestFactory.class);
    private BaseHttpClientFactory httpClientFactory;
    private CoordinatorClientImpl coordinator;
    private AbstractHttpClient httpClient;
    private JAXBContext marshallingJaxbContext;
    private JAXBContext unmarshallingJaxbContext;
    
    public ClientHttpRequestFactory(){
    	
    }

    public JAXBContext getMarshallingJaxbContext() {
		return marshallingJaxbContext;
	}

	public void setMarshallingJaxbContext(JAXBContext marshallingJaxbContext) {
		this.marshallingJaxbContext = marshallingJaxbContext;
	}

	public JAXBContext getUnmarshallingJaxbContext() {
		return unmarshallingJaxbContext;
	}

	public void setUnmarshallingJaxbContext(JAXBContext unmarshallingJaxbContext) {
		this.unmarshallingJaxbContext = unmarshallingJaxbContext;
	}

	public BaseHttpClientFactory getHttpClientFactory() {
        return httpClientFactory;
    }

    public void setHttpClientFactory(BaseHttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }

    public ClientHttpRequest create() throws ClientGeneralException {
        return new ClientHttpRequest(httpClient, marshallingJaxbContext,unmarshallingJaxbContext);
    }    
        
    private AbstractHttpClient createUnconfiguredClient() throws ClientGeneralException { 
        AbstractHttpClient httpClient = null;
        try {
            httpClient = httpClientFactory.createHTTPClient();
            HttpProtocolParams.setUseExpectContinue(httpClient.getParams(), true);
            return httpClient;
            
        } catch (Exception ex) {
            LOGGER.error("Error initializing new HttpClient instance");
            throw new ClientGeneralException(ClientMessageKeys.UNEXPECTED_FAILURE, new String[] { ex.getMessage() });
        }
    }
        
    @Override
    public void afterPropertiesSet() throws Exception {
        StringBuilder errorMessage = new StringBuilder("factory requires injection of non-null ");
        boolean error = false;
        if (httpClientFactory == null) {
            errorMessage.append("httpClientFactory ");
            error = true;
        }
        
        if (marshallingJaxbContext == null) {
            errorMessage.append("marshallingJaxbContext ");
            error = true;
        } 
        
        if (unmarshallingJaxbContext == null) {
            errorMessage.append("unmarshallingJaxbContext ");
            error = true;
        }  
        
        if (error) {
            throw new IllegalArgumentException(errorMessage.toString());
        }
        
        this.httpClient = createUnconfiguredClient();
    }
}
