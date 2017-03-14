/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.ntlm.state;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpRequest;
import org.apache.http.protocol.HttpContext;

import com.iwave.ext.windows.winrm.ntlm.NTLMMessage;
import com.iwave.ext.windows.winrm.ntlm.NTLMType1Message;
import com.iwave.ext.windows.winrm.ntlm.NTLMUtils;

/**
 * This is the state that we will be in immediately after receiving the initial negotiate message and prior to sending a type
 * 1 message. We will only accept HttpRequests that have an NTLM type 1 message.
 */
public final class SendingType1State extends NTLMState {

    /** This state. */
    protected static final SendingType1State INSTANCE = new SendingType1State();

    /** The message as it appears on the wire. */
    private byte[] msg1;

    /**
     * Private constructor.
     */
    private SendingType1State() {

    }

    @Override
    public boolean accepts(HttpRequest request) {
        // We only accept HttpRequests that are sending an NTLM type 1 message
    	try {
        NTLMMessage message = NTLMUtils.getNTLMMessage(request);
        if (message != null && message.getMessageType() == NTLMType1Message.TYPE) {
            return true;
        }
    	} catch (Exception e) {
    		
    	}
        return false;
    }

    @Override
    public void handle(HttpRequest request, HttpClientConnection conn, HttpContext context) {
        NTLMMessage msg = NTLMUtils.getNTLMMessage(request);
        msg.addFlag(NTLMUtils.NEGOTIATE_KEY_EXCHANGE);
        msg.addFlag(NTLMUtils.NEGOTIATE_SEAL);
        msg.addFlag(NTLMUtils.NEGOTIATE_SIGN);
        request.setHeader(NTLMUtils.buildNtlmHeader(msg));

        msg1 = NTLMUtils.getRawNTLMMessage(request);
    }

    @Override
    public NTLMState getNextState() {
        return new ReceivingType2State(msg1);
    }

}
