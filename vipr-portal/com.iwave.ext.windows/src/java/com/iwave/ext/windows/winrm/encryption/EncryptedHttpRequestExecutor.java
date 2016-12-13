/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.encryption;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpRequestExecutor;

import com.iwave.ext.windows.winrm.ntlm.NTLMEncryptedRequestHandler;

/**
 * This class takes the Http Request and puts it on the wire. It is at this point that we determine if we want to
 * encrypt/decrypt the messages. The basic flow for messages is (we are client):
 * 
 * <pre>
 * Client -----Request-----> Host
 * Client <-----401--------- Host
 * Client --Initiate Auth--> Host
 * ... Details specific to the auth implementation ...
 * </pre>
 * 
 * Within the 401 message will be a header indicating the types of negotiation that are available. Based on this we can
 * determine how/if the messages will be encrypted.
 * 
 * @author Jason Forand
 *
 */
public class EncryptedHttpRequestExecutor extends HttpRequestExecutor {

    /**
     * Since there is no guarantee that this executor will be recreated/reinitialized for each host we connect to, we need to
     * keep one request handler for each connection.
     */
    private Map<Object, EncryptedRequestHandler> handlers = new HashMap<Object, EncryptedRequestHandler>();

    /**
     * Determines whether the communication should be encrypted or not. Setting this to false is only recommended if there is
     * some sort of bug that arises in production.
     */
    private static final boolean ENCRYPTED = Boolean.parseBoolean(System.getProperty("http.ntlm.encrypted", "true"));

    @Override
    protected HttpResponse doSendRequest(HttpRequest request, HttpClientConnection conn, HttpContext context)
            throws IOException, HttpException {
        if (ENCRYPTED) {
            EncryptedRequestHandler handler = getHandler(request, conn, context);
            if (handler != null) {
                handler.alterRequest(request, conn, context);
            }
        }
        return super.doSendRequest(request, conn, context);
    }

    @Override
    protected HttpResponse doReceiveResponse(HttpRequest request, HttpClientConnection conn, HttpContext context)
            throws HttpException, IOException {
        HttpResponse response = super.doReceiveResponse(request, conn, context);
        if (ENCRYPTED) {
            EncryptedRequestHandler handler = getHandler(response, conn, context);
            if (handler != null) {
                handler.alterResponse(response, conn, context);
            }
        }
        return response;
    }

    /**
     * Retrieves the handler for the connection, or creates a new one.
     * 
     * @param message
     *            the message to process
     * @param conn
     *            the connection to retrieve the handler for
     * @param context
     *            the context to retrieve the handler for
     * @return an encrypted request handler that can process the message, or null if one doesn't exist
     */
    private EncryptedRequestHandler getHandler(HttpMessage message, HttpClientConnection conn, HttpContext context) {
        // This is our guarantee of unicity. We are unable to use connection, because it changes occasionally.
        HttpHost host = (HttpHost) context.getAttribute(HttpCoreContext.HTTP_TARGET_HOST);
        if (host == null) {
            throw new RuntimeException("Context does not contain a host.");
        }
        EncryptedRequestHandler handler = handlers.get(host);
        if (handler == null || !handler.accepts(message)) {
        	handler = null;
            handlers.remove(host);
            for (EncryptedRequestHandler h : getAvailableHandlers()) {
                if (h.accepts(message)) {
                    handler = h;
                    handlers.put(host, handler);
                    break;
                }
            }
        }
        return handler;
    }

    /**
     * Retrieves all of the possible virgin request handlers.
     * 
     * @return the request handlers
     */
    private EncryptedRequestHandler[] getAvailableHandlers() {
        // Each of our handlers is stateful, so in the event that they can accept the message, we need a new instance of the
        // handler in order to start preserving state, rather than just a static method to check if the message is acceptable
        return new EncryptedRequestHandler[] { new NTLMEncryptedRequestHandler() };
    }
}