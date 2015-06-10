/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.platform.clientlib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XMLHttpResponseHandler<T> implements ResponseHandler<T> {

	private static final Logger LOGGER = LoggerFactory.getLogger(XMLHttpResponseHandler.class);
		
	String request;
	String payload;
	JAXBContext jaxbContext;
	
	public XMLHttpResponseHandler( JAXBContext jaxbContext, String request, String payload) {
	    this.request = request;
	    this.payload = payload;
	    this.jaxbContext = jaxbContext;
	}
	
	@Override
	public T handleResponse(HttpResponse response) throws ClientProtocolException, IOException {	
		return getXMLPayload(response);
	}
	
	@SuppressWarnings("rawtypes")
    private T getXMLPayload(HttpResponse response) throws IOException {
	    HttpEntity entity = response.getEntity();
	    Integer statusCode = response.getStatusLine().getStatusCode();
	    	    
	    if (statusCode >= 300) {
	        String rawResponse = getErrorString(entity);
	        String errorMessage = null;
	        boolean htmlResponse = false;
            Header contentType = response.getLastHeader("Content-type");
            if ((contentType != null) && (contentType.getValue().startsWith("text/html"))) {
                htmlResponse = true;
            }
            if (htmlResponse || (rawResponse == null)) {
                errorMessage = response.getStatusLine().getReasonPhrase();
                if (rawResponse != null) {
                    LOGGER.debug("Raw HTML Response: {}", rawResponse);                    
                }                
            } else {
                errorMessage = rawResponse;
            }
            LOGGER.error("Platform returned error status " + statusCode + " with message: " + errorMessage);
            throw new ClientHttpResponseException(statusCode, errorMessage, request, payload);
        }
	    
	    
	    InputStream ins = null;
        if (entity != null)  {
            ins = entity.getContent();
            if (ins == null) return null;
        } else return null; 
        BufferedReader reader = new BufferedReader(new InputStreamReader(ins));
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            JAXBElement<T> jaxbElement = (JAXBElement<T>)unmarshaller.unmarshal(reader);
            
            if(jaxbElement !=null){
            	return jaxbElement.getValue();
            }
        }catch (JAXBException e) {
        	LOGGER.debug(e.getLocalizedMessage(),e);
        	return null;
		}
        return null;
	}
	
	private String getErrorString(HttpEntity entity) {
	    try {
	        return EntityUtils.toString(entity);
	    } catch (Exception ex) {
	        LOGGER.error("exception while reading error string", ex);
	        return null;
	    }
	}
}
