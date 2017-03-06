/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.ntlm.state;

import org.apache.http.Header;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;

import com.iwave.ext.windows.winrm.ntlm.NTLMConstants;
import com.iwave.ext.windows.winrm.ntlm.NTLMCrypt;
import com.iwave.ext.windows.winrm.ntlm.NTLMDecryptedEntity;
import com.iwave.ext.windows.winrm.ntlm.NTLMUtils;

/**
 * This state will ping-pong back and forth with EncryptingState in order to continue encrypting/decrypting messages. We will
 * know that the message needs to be decrypted because there will be a header with an element
 * protocol=application/HTTP-SPNEGO-session-encrypted
 */
public final class DecryptingState extends NTLMState {

    /** The NTLM crypt mechanism to use to encrypt/decrypt messages. */
    private NTLMCrypt crypt;

    /**
     * Instantiates a state that will handle all decryption for NTLM.
     * 
     * @param crypt
     *            the encryption mechanism to use
     */
    public DecryptingState(NTLMCrypt crypt) {
        this.crypt = crypt;
    }

    @Override
    public boolean accepts(HttpResponse response) {
        // This probably isn't a perfect test; it accepts any Content-Type header that claims to be SPNEGO
        Header[] headers = response.getHeaders(HttpHeaders.CONTENT_TYPE);
        for (Header header : headers) {
            if (header != null && header.getValue().contains(NTLMConstants.CONTENT_TYPE_FOR_SPNEGO)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void handle(HttpResponse response, HttpClientConnection conn, HttpContext context) {
        String boundary = NTLMUtils.getBoundary(response);
        if (boundary == null) {
            throw new RuntimeException("Unable to find boundary in http message");
        }
        NTLMDecryptedEntity entity = new NTLMDecryptedEntity(response.getEntity(), crypt, boundary);
        // Set the content type header in our response to the content type of the decrypted message
        response.setHeader(entity.getContentType());
        response.setEntity(entity);
    }

    @Override
    public NTLMState getNextState() {
        return new EncryptingState(crypt);
    }

}
