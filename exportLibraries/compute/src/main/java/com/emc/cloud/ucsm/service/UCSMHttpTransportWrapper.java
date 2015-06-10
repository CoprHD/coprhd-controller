/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.ucsm.service;

import com.emc.cloud.platform.clientlib.ClientMessageKeys;
import java.net.MalformedURLException;
import java.net.URL;
import javax.xml.bind.JAXBElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.cloud.platform.clientlib.ClientGeneralException;
import com.emc.cloud.platform.clientlib.ClientHttpRequest;
import com.emc.cloud.platform.clientlib.ClientHttpRequestFactory;

public class UCSMHttpTransportWrapper implements TransportWrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(UCSMHttpTransportWrapper.class);

    @Autowired
    private ClientHttpRequestFactory ucsTransportRequestFactory;

    @Autowired
    private ClientHttpRequestFactory ucsSSLTransportRequestFactory;

    public <T> T execute(Object device,Object payload,Class<T> returnType) throws ClientGeneralException{
        return postEntity((String)device, (JAXBElement<T>)payload,returnType);
    }

    public <T> T postEntity(String serviceURI,JAXBElement<T> jaxbElement,Class<T> returnType) throws ClientGeneralException{
        URL ucsmURL =null;
        ClientHttpRequest httpRequest =null;

        try{
            ucsmURL = new URL(serviceURI);
        }catch (MalformedURLException ex){
            throw new ClientGeneralException(ClientMessageKeys.MALFORMED_URL);
        }

        if(ucsmURL.getProtocol().equalsIgnoreCase("https")){
            httpRequest = ucsSSLTransportRequestFactory.create();
        }else{
            httpRequest = ucsTransportRequestFactory.create();
        }

        T result = null;

        try {
            result = httpRequest.httpPostXMLObject(serviceURI,jaxbElement,returnType);
        } catch (ClientGeneralException e) {
            LOGGER.info(e.getLocalizedMessage(),e);
            throw e;
        }
        return result;
    }

}
