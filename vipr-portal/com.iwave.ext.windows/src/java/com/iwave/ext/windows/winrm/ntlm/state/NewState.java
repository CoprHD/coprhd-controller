/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.ntlm.state;

import org.apache.http.Header;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AUTH;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.protocol.HttpContext;

/**
 * This state is our starting state. It will only move to the next state if it processes an HttpResponse that has the header
 * WWW-Authenticate: Negotiate
 *
 */
public final class NewState extends NTLMState {

    /** This state. */
    protected static final NewState INSTANCE = new NewState();

    /**
     * Private constructor.
     */
    private NewState() {

    }

    @Override
    public boolean accepts(HttpResponse response) {
        // We only accepts responses that are 401's and contain the header WWW-Authenticate == "Negotiate"
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
            Header[] headers = response.getHeaders(AUTH.WWW_AUTH);
            for (Header header : headers) {
                if (header.getValue().trim().equalsIgnoreCase(AuthSchemes.SPNEGO)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void handle(HttpRequest request, HttpClientConnection conn, HttpContext context) {
        // No need to do anything here, but since this is our initial state, we need to handle all messages to make sure no
        // exception are thrown
    };

    @Override
    public void handle(HttpResponse response, HttpClientConnection conn, HttpContext context) {
        // Don't need to do anything here, just need to make sure that we handle the response so that no exception is thrown
    };

    @Override
    public NTLMState getNextState() {
        return SendingType1State.INSTANCE;
    }

}
