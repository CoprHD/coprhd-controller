/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.ntlm.state;

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iwave.ext.windows.winrm.ntlm.NTLMCrypt;
import com.iwave.ext.windows.winrm.ntlm.NTLMMessage;
import com.iwave.ext.windows.winrm.ntlm.NTLMType3Message;
import com.iwave.ext.windows.winrm.ntlm.NTLMUtils;
import com.iwave.ext.windows.winrm.ntlm.TargetInformation;

/**
 * This is the state that we will be in immediately after sending after receiving a type 2 message and prior to sending a
 * type 3 message. It will only accept an HttpRequest that has an NTLM type 3 message.
 *
 */
public final class SendingType3State extends NTLMState {

    /**
     * The logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(SendingType3State.class);

    /** The bytes from the type 1 message as they appeared on the wire. */
    private byte[] msg1;
    /** The bytes from the type 2 message as they appeared on the wire. */
    private byte[] msg2;
    /** The server challenge. */
    private byte[] challenge;
    /** The target information from the server. */
    private TargetInformation information;
    /** The next state. */
    private NTLMState next;

    /**
     * Constructor for the type 3 message.
     * 
     * @param msg1
     *            the type 1 message
     * @param msg2
     *            the type 2 message
     * @param challenge
     *            the challenge from the server
     * @param information
     *            the target information from the server
     */
    public SendingType3State(byte[] msg1, byte[] msg2, byte[] challenge, TargetInformation information) {
        this.msg1 = msg1;
        this.msg2 = msg2;
        this.challenge = challenge;
        this.information = information;
    }

    @Override
    public boolean accepts(HttpRequest request) {
        // Only accept HttpRequests that have an NTLM type 3 message
        NTLMMessage msg = NTLMUtils.getNTLMMessage(request);
        if (msg != null && msg.getMessageType() == NTLMType3Message.TYPE) {
            return true;
        }
        return false;
    }

    @Override
    public void handle(HttpRequest request, HttpClientConnection conn, HttpContext context) {
        try {
            NTLMType3Message type3 = (NTLMType3Message) NTLMUtils.getNTLMMessage(request);
            HttpHost host = (HttpHost) context.getAttribute(HttpCoreContext.HTTP_TARGET_HOST);
            CredentialsProvider provider = (CredentialsProvider) context.getAttribute(HttpClientContext.CREDS_PROVIDER);
            String password;
            String user;
            String domain = "";
            if (host == null) {
                password = provider.getCredentials(AuthScope.ANY).getPassword();
                user = provider.getCredentials(AuthScope.ANY).getUserPrincipal().getName();
            } else {
                password = provider.getCredentials(new AuthScope(host)).getPassword();
                user = provider.getCredentials(new AuthScope(host)).getUserPrincipal().getName();
            }
            if (user != null) {
                // DOMAIN \ USER
                String[] parts = StringUtils.split(user, '\\');
                if (parts.length == 2) {
                    user = parts[1];
                    domain = parts[0];
                }
            } else {
                throw new RuntimeException("Anonymous NTLM sessions are not supported. You must provide a user.");
            }
            NTLMCrypt crypt;
            // If the timestamp is empty we make one
            if (information.get(TargetInformation.MSV_AV_TIMESTAMP) == null) {
                information.put(TargetInformation.MSV_AV_TIMESTAMP,
                        NTLMUtils.toMicrosoftTimestamp(System.currentTimeMillis()));
            }

            // https://msdn.microsoft.com/en-us/library/cc236700.aspx
            // https://code.google.com/p/ntlm-java/
            // The above two links are the only resources I was able to find to help me understand ntlmv2. The microsoft one
            // isn't as good as the google code one, but the google code one is incomplete, so it's a good place to start
            // understanding, but then you will need the microsoft one to complete the protocol

            // This it to indicate we are providing a MIC in the header
            // https://msdn.microsoft.com/en-us/library/cc236646.aspx
            information.put(TargetInformation.MSV_AV_FLAGS, NTLMUtils.convertInt(2));
            // Need to specify the domain we are targeting, http-client doesn't populate this one
            type3.setTargetname(domain.getBytes(CharEncoding.UTF_16LE));
            // Save the nonce because it comes from the lmresp, which will get overwritten with 0's shortly
            byte[] nonce = type3.getNonce();

            // This is the calculation for the NTLMv2 key, the details of which can be found at
            // https://msdn.microsoft.com/en-us/library/cc236700.aspx
            byte[] responseKeyNT = NTLMUtils.calculateHmacMD5(NTLMUtils.md4(password.getBytes(CharEncoding.UTF_16LE)),
                    NTLMUtils.concat(user.toUpperCase().getBytes(CharEncoding.UTF_16LE),
                            domain.getBytes(CharEncoding.UTF_16LE)));
            byte[] temp = NTLMUtils.concat(new byte[] { 0x01 }, new byte[] { 0x01 }, new byte[6],
                    information.get(TargetInformation.MSV_AV_TIMESTAMP), nonce, new byte[4], information.build());
            byte[] ntProofStr = NTLMUtils.calculateHmacMD5(responseKeyNT, NTLMUtils.concat(challenge, temp));
            byte[] keyExchangeKey = NTLMUtils.calculateHmacMD5(responseKeyNT, ntProofStr);
            byte[] rkey = NTLMUtils.rc4(type3.getSessionkey(), keyExchangeKey);
            byte[] ntChallengeResponse = NTLMUtils.concat(ntProofStr, temp);

            // NTLMv2 specifies these values for the ntlm and lm resp. Lm must be 0's
            type3.setNtlmresp(ntChallengeResponse);
            type3.setLmresp(new byte[24]);
            // MIC is a message integrity check, and it must be calculated with the MIC set to 0
            type3.setMic(new byte[16]);
            // This is the calculation for the mic, the details of which can be found at
            // https://technet.microsoft.com/ru-ru/cc236678
            type3.setMic(NTLMUtils.calculateHmacMD5(rkey, NTLMUtils.concat(msg1, msg2, type3.toHeaderBytes())));
            crypt = new NTLMCrypt(rkey);

            // This is NTLM v1. Keeping it here in case it's ever needed, but it shouldn't be
            // Change the header value, as http client is generating it wrong
            // type3.setNtlmresp(NTLMUtils.getNTLM2SessionResponse(password, challenge, type3.getNonce()));
            // crypt = new NTLMCrypt(
            // NTLMUtils.calculateV1Key(challenge, type3.getNonce(), type3.getSessionkey(), password));

            // Reset the header
            request.setHeader(NTLMUtils.buildNtlmHeader(type3));

            // If we got to this point, we will be encrypting all communication, including this message
            EncryptingState state = new EncryptingState(crypt);
            state.handle(request, conn, context);
            next = state.getNextState();
        } catch (Exception e) {
            LOG.error("Could not initialize encryption mechanism.", e);
            next = NewState.INSTANCE;
        }
    }

    @Override
    public NTLMState getNextState() {
        return next;
    }
}
