/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.platform.clientlib;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class XMLHttpResponseHandler<T> implements ResponseHandler<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(XMLHttpResponseHandler.class);

    String request;
    String payload;
    JAXBContext jaxbContext;

    public XMLHttpResponseHandler(JAXBContext jaxbContext, String request, String payload) {
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
        if (entity != null) {
            ins = entity.getContent();
            if (ins == null) {
                return null;
            }
        } else {
            return null;
        }
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setExpandEntityReferences(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document document = db.parse(ins);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            JAXBElement<T> jaxbElement = (JAXBElement<T>) unmarshaller.unmarshal(document);

            if (jaxbElement != null) {
                return jaxbElement.getValue();
            }
        } catch (JAXBException e) {
            LOGGER.debug(e.getLocalizedMessage(), e);
            return null;
        } catch (ParserConfigurationException | SAXException ex)
        {
            LOGGER.error("Unable to parse XML content - {}", ex.getMessage());
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
