/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
/**
 * 
 */
package com.iwave.ext.netapp;

import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.ClientResponse;

/**
 * @author lakhiv
 * 
 */
public class NetAppClient {

    private RestClient client;
    private final Logger _log = LoggerFactory.getLogger(NetAppClient.class);

    private static final URI URI_NETAPP_SERVICE = URI.create("/servlets/netapp.servlets.admin.XMLrequest_filer");
    private static final String header = "<netapp version='1.19' xmlns='http://www.netapp.com/filer/admin'>";
    private static final String footer = "</netapp>";

    public NetAppClient(RestClient restClient) {
        client = restClient;
    }

    public ClientResponse sendRequest(String cmd, Map<String, String> args) {
        ClientResponse clientResp = null;

        try {
            String xmlBody = getXMLRequestBody(cmd, args);
            clientResp = client.post(URI_NETAPP_SERVICE, xmlBody);

        } catch (Exception ex) {
            _log.error("Failed to process request " + cmd + " with error : " + ex.getMessage());
        }
        return clientResp;
    }

    public String getXMLRequestBody(String cmd, Map<String, String> args) {
        StringBuilder xmlRequest = new StringBuilder();

        xmlRequest.append(header);

        if (args.isEmpty()) {
            xmlRequest.append("<").append(cmd).append("/>");
        } else {
            xmlRequest.append("<").append(cmd).append(">");
            for (Entry<String, String> entry : args.entrySet()) {
                xmlRequest.append("<").append(entry.getKey()).append(">");
                xmlRequest.append(entry.getValue());
                xmlRequest.append("</").append(entry.getKey()).append(">");
            }
            xmlRequest.append("</").append(cmd).append(">");

        }
        xmlRequest.append(footer);
        return xmlRequest.toString();
    }

}