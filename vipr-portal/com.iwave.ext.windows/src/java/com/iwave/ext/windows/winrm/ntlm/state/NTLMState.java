/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.ntlm.state;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;

/**
 * State machine for NTLM messages. The ordering of the messages is as follows.
 * 
 * <pre>
 * ------- Initial Request ------>
 * <----------- 401 --------------
 * ------ NTLM Type 1 Message --->
 * <----- NTLM Type 2 Message ----
 * ------ NTLM Type 3 Message ---> (+ encrypted payload)
 * <----- Encrypted Message ------
 * ------ Encrypted Message ----->
 * ..... Continue the encrypted messages
 * </pre>
 * 
 * Our state machine will start when receiving the 401.
 *
 */
public abstract class NTLMState {

    /** The initial state of the state machine. */
    public static final NTLMState INITIAL = NewState.INSTANCE;

    /**
     * Determines whether or not this state will accept this request.
     * 
     * @param request
     *            the request to process
     * @return true if we can process it
     */
    public boolean accepts(HttpRequest request) {
        return false;
    }

    /**
     * Determines whether or not this state will accept this response.
     * 
     * @param response
     *            the response to process
     * @return true if we can process it
     */
    public boolean accepts(HttpResponse response) {
        return false;
    }

    /**
     * Performs the necessary operations to this request.
     * 
     * @param request
     *            the request
     * @param conn
     *            the connection
     * @param context
     *            the context
     */
    public void handle(HttpRequest request, HttpClientConnection conn, HttpContext context) {
        throw new RuntimeException(this + " cannot handle " + request);
    }

    /**
     * Performs the necessary operations to this response.
     * 
     * @param response
     *            the response
     * @param conn
     *            the connection
     * @param context
     *            the context
     */
    public void handle(HttpResponse response, HttpClientConnection conn, HttpContext context) {
        throw new RuntimeException(this + " cannot handle " + response);
    }

    /**
     * Returns the next state in the state machine.
     * 
     * @return the next state of the machine
     */
    public abstract NTLMState getNextState();

}
