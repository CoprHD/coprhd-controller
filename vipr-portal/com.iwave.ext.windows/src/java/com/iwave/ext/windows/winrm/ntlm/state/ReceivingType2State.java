/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.ntlm.state;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;

import com.iwave.ext.windows.winrm.ntlm.NTLMMessage;
import com.iwave.ext.windows.winrm.ntlm.NTLMType2Message;
import com.iwave.ext.windows.winrm.ntlm.NTLMUtils;
import com.iwave.ext.windows.winrm.ntlm.TargetInformation;

/**
 * This is the state that we will be in immediately after sending a type 1 message while we wait for a type 2 message. We
 * will only accept Http Responses that have an NTLM type 2 message.
 */
public final class ReceivingType2State extends NTLMState {

    /** The type 1 message as it appears on the wire. */
    private byte[] msg1;
    /** The type 2 message as it appears on the wire. */
    private byte[] msg2;
    /** The server challenge. */
    private byte[] challenge;
    /** The target information from the server. */
    private TargetInformation information;

    /**
     * Constructor for this state.
     * 
     * @param msg1
     *            the type 1 message as it appears on the wire
     */
    public ReceivingType2State(byte[] msg1) {
        this.msg1 = msg1;
    }

    @Override
    public boolean accepts(HttpResponse response) {
        // Only accept HttpResponse's that have an NTLM type 2 message
        NTLMMessage msg = NTLMUtils.getNTLMMessage(response);
        if (msg != null && msg.getMessageType() == NTLMType2Message.TYPE) {
            return true;
        }
        return false;
    }

    @Override
    public void handle(HttpResponse response, HttpClientConnection conn, HttpContext context) {
        NTLMType2Message msg = (NTLMType2Message) NTLMUtils.getNTLMMessage(response);
        if (msg.hasFlag(NTLMUtils.NEGOTIATE_SEAL)) {
            this.challenge = msg.getChallenge();
            this.information = msg.getTargetInformation();
        }

        this.msg2 = NTLMUtils.getRawNTLMMessage(response);
    }

    @Override
    public NTLMState getNextState() {
        if (challenge != null) {
            return new SendingType3State(msg1, msg2, challenge, information);
        } else {
            return NewState.INITIAL;
        }
    }

}
