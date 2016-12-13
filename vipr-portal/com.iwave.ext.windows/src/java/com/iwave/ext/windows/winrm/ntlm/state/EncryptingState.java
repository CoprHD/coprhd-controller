/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.ntlm.state;

import org.apache.http.Header;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHeaderElement;
import org.apache.http.message.BasicHeaderValueFormatter;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;

import com.iwave.ext.windows.winrm.ntlm.NTLMConstants;
import com.iwave.ext.windows.winrm.ntlm.NTLMCrypt;
import com.iwave.ext.windows.winrm.ntlm.NTLMEncryptedEntity;

/**
 * This state will ping-pong back and forth with DecryptingState in order to continue encrypting/decrypting messages. We need
 * to encrypt all requests
 */
public class EncryptingState extends NTLMState {

    /** The NTLM crypt mechanism to use to encrypt/decrypt messages. */
    private NTLMCrypt crypt;

    /**
     * The boundary. Although testing has indicated that any value would work, the specification found at
     * https://msdn.microsoft.com/en-us/library/cc251576.aspx explicitly states that "Encrypted Boundary" must be used, so
     * we'll use that.
     */
    public static final String BOUNDARY = "Encrypted Boundary";

    /**
     * Instantiates a state that will handle encryption for NTLM.
     * 
     * @param crypt
     *            the encryption mechanism to use
     */
    public EncryptingState(NTLMCrypt crypt) {
        this.crypt = crypt;
    }

    @Override
    public boolean accepts(HttpRequest message) {
        // The communication has been determined to be encrypted, so we need to send everything encrypted
        return true;
    }

    @Override
    public void handle(HttpRequest request, HttpClientConnection conn, HttpContext context) {
        request.setHeader(buildContentTypeHeader());
        HttpEntityEnclosingRequest wrapper = (HttpEntityEnclosingRequest) request;
        wrapper.setEntity(new NTLMEncryptedEntity(wrapper.getEntity(), crypt, BOUNDARY));
        // Need to modify the content length of the request, otherwise there are decryption problems
        request.setHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(wrapper.getEntity().getContentLength()));
    }

    /**
     * Builds the new content type header. This is as per NTLM specification.
     * 
     * @return the new content type header
     */
    private Header buildContentTypeHeader() {
        NameValuePair[] nvps = new NameValuePair[] {
                new BasicNameValuePair(NTLMConstants.PROTOCOL, NTLMConstants.CONTENT_TYPE_FOR_SPNEGO),
                new BasicNameValuePair(NTLMConstants.BOUNDARY, BOUNDARY) };
        BasicHeaderElement elem = new BasicHeaderElement(NTLMConstants.CONTENT_TYPE_FOR_MULTIPART_ENCRYPTED, null, nvps);
        return new BasicHeader(HttpHeaders.CONTENT_TYPE, BasicHeaderValueFormatter.formatHeaderElement(elem, false, null));
    }

    @Override
    public NTLMState getNextState() {
        return new DecryptingState(crypt);
    }

}
