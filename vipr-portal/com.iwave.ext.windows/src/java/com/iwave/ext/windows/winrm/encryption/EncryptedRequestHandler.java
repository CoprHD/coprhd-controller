/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.encryption;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;

/**
 * Describes the steps that must be taken when encrypting/decrypting an HttpRequest. Depending on the protocol, the entire
 * request needs to be re-written.
 *
 */
public interface EncryptedRequestHandler {

    /**
     * Given the base HttpRequest, alter it so that it is in it's encrypted format.
     * 
     * @param request
     *            the request to alter
     * @param conn
     *            the connection that will be used
     * @param context
     *            the context of the request
     */
    void alterRequest(HttpRequest request, HttpClientConnection conn, HttpContext context);

    /**
     * Given the (most likely encrypted) HttpResponse, alter it so that it is back in plaintext format.
     * 
     * @param response
     *            the response to alter
     * @param conn
     *            the connection that was used
     * @param context
     *            the context of the response
     */
    void alterResponse(HttpResponse response, HttpClientConnection conn, HttpContext context);

    /**
     * Determines whether this type of message can be processed.
     * 
     * @param message
     *            the message to be evaluated
     * @return true if we can process the message
     */
    boolean accepts(HttpMessage message);

}