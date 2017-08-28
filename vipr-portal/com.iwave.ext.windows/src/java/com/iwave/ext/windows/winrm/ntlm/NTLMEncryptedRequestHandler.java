/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.ntlm;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iwave.ext.windows.winrm.encryption.EncryptedRequestHandler;
import com.iwave.ext.windows.winrm.entity.RepeatableEntity;
import com.iwave.ext.windows.winrm.ntlm.state.NTLMState;

/**
 * This class handles the specifics of ensuring that the request/response match the NTLM specifications.
 */
public class NTLMEncryptedRequestHandler implements EncryptedRequestHandler {

    /**
     * The logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(NTLMEncryptedRequestHandler.class);

    /** The state of communication. This should help to validate that we are processing the correct messages. */
    private NTLMState state = NTLMState.INITIAL;

    @Override
    public void alterRequest(HttpRequest request, HttpClientConnection conn, HttpContext context) {
        if (LOG.isDebugEnabled()) {
            logRequest(request);
        }
        state.handle(request, conn, context);
        state = state.getNextState();
    }

    @Override
    public void alterResponse(HttpResponse response, HttpClientConnection conn, HttpContext context) {
        if (LOG.isDebugEnabled()) {
            logResponse(response);
        }
        state.handle(response, conn, context);
        state = state.getNextState();
    }

    /**
     * Logs the http request for debugging purposes.
     * 
     * @param request
     *            the request to log
     */
    private void logRequest(HttpRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("<Request>");
        for (Header header : request.getAllHeaders()) {
            builder.append("<Header>").append(header.getName()).append(": ").append(header.getValue()).append("</Header>");
        }
        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntityEnclosingRequest req = (HttpEntityEnclosingRequest) request;
            if (req.getEntity() != null) {
                if (!req.getEntity().isRepeatable()) {
                    // This is risky
                    req.setEntity(new RepeatableEntity(req.getEntity()));
                }
                try {
                    builder.append("<Entity>")
                            .append(Hex.encodeHexString(IOUtils.toByteArray(req.getEntity().getContent())))
                            .append("</Entity>");
                } catch (Exception e) {
                    builder.append("There was an exception while attempting to read the response entity.");
                }
            }
        }
        builder.append("</Request>");
        LOG.debug(builder.toString());
    }

    /**
     * Logs the HttpResponse for debugging purposes.
     * 
     * @param response
     *            the response to log
     */
    private void logResponse(HttpResponse response) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n");
        builder.append("<Response>");
        builder.append("\n");
        for (Header header : response.getAllHeaders()) {
            builder.append("<Header>").append(header.getName()).append(": ").append(header.getValue()).append("</Header>");
            builder.append("\n");
        }
        if (response.getEntity() != null) {
            if (!response.getEntity().isRepeatable()) {
                // This is risky
                response.setEntity(new RepeatableEntity(response.getEntity()));
            }
            try {
                builder.append("<Entity>")
                        .append(Hex.encodeHexString(IOUtils.toByteArray(response.getEntity().getContent())))
                        .append("</Entity>");
            } catch (Exception e) {
                builder.append("There was an exception while attempting to read the response entity.");
            }
        }
        builder.append("\n");
        builder.append("</Response>");
        LOG.debug(builder.toString());
    }

    @Override
    public boolean accepts(HttpMessage message) {
        if (message instanceof HttpRequest) {
            return state.accepts((HttpRequest) message);
        } else if (message instanceof HttpResponse) {
            return state.accepts((HttpResponse) message);
        } else {
            return false;
        }
    }
}
