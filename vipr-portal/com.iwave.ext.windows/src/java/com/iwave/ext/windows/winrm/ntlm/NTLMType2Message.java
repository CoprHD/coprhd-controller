/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.ntlm;

import java.util.Arrays;

import org.apache.commons.lang3.CharEncoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class representing an NTLM Type 2 Message. The format of the message is as follows.
 * 
 * <pre>
 *              Description             Content     
 *   0          NTLMSSP Signature       Null-terminated ASCII "NTLMSSP" (0x4e544c4d53535000)
 *   8          NTLM Message Type       long (0x02000000)
 *  12          Target Name             security buffer
 *  20          Flags                   long
 *  24          Challenge               8 bytes
 * (32)         Context (optional)      8 bytes (two consecutive longs)
 * (40)         Target Information      (optional)   security buffer
 * (48)         OS Version Structure    (Optional) 8 bytes
 * 32 (48) (56) start of data block
 * </pre>
 * 
 * @author Jason Forand
 *
 */
public class NTLMType2Message extends NTLMMessage {

    /**
     * The logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(NTLMType2Message.class);

    /** Target byte array. */
    private byte[] target;
    /** Server challenge byte array. */
    private byte[] challenge;
    /** Context byte array. */
    private byte[] context;
    /** Target Information byte array. */
    private TargetInformation targetInformation;
    /** The type of this NTLM message. */
    public static final int TYPE = 2;

    /**
     * 
     * @param target
     *            the target
     * @param flags
     *            the flags
     * @param challenge
     *            the challenge
     * @param context
     *            the context
     * @param targetInformation
     *            the target information
     * @param version
     *            the version
     */
    public NTLMType2Message(byte[] target, byte[] flags, byte[] challenge, byte[] context,
            TargetInformation targetInformation, byte[] version) {
        super(flags, version);
        this.target = target;
        this.challenge = challenge;
        this.context = context;
        this.targetInformation = targetInformation;
    }

    /**
     * @return the target
     */
    public byte[] getTarget() {
        return target;
    }

    /**
     * @param target
     *            the target to set
     */
    public void setTarget(byte[] target) {
        this.target = target;
    }

    /**
     * @return the challenge
     */
    public byte[] getChallenge() {
        return challenge;
    }

    /**
     * @param challenge
     *            the challenge to set
     */
    public void setChallenge(byte[] challenge) {
        this.challenge = challenge;
    }

    /**
     * @return the context
     */
    public byte[] getContext() {
        return context;
    }

    /**
     * @param context
     *            the context to set
     */
    public void setContext(byte[] context) {
        this.context = context;
    }

    /**
     * @return the targetInformation
     */
    public TargetInformation getTargetInformation() {
        return targetInformation;
    }

    /**
     * @param targetInformation
     *            the targetInformation to set
     */
    public void setTargetInformation(TargetInformation targetInformation) {
        this.targetInformation = targetInformation;
    }

    @Override
    public int getMessageType() {
        return TYPE;
    }

    /**
     * Pretty print for debugging purposes.
     * 
     * @return the pretty string
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("TYPE 2\n");
        try {
            builder.append("TARGET: ").append(new String(target, CharEncoding.UTF_16LE)).append("\n");
        } catch (Exception e) {
            LOG.error("Impossible encoding exception.", e);
        }
        builder.append(getFlagsAsString());
        builder.append(getVersionAsString());
        return builder.toString();
    }

    @Override
    public byte[] toHeaderBytes() {
        NTLMByteMessage message = new NTLMByteMessage();

        // This is the stuff that goes into the body
        message.putHeader(NTLMSSP_BYTES);
        message.putHeader(NTLMUtils.convertInt(2));
        message.putData(target);
        message.putHeader(getFlags());
        message.putHeader(challenge);
        message.putHeader(context);
        message.putData(targetInformation.build());
        message.putHeader(getVersion());

        return message.getMsg();
    }

    /**
     * Parses a byte header and creates a type 2 message out of it.
     * 
     * @param header
     *            the byte header
     * @return the created message
     */
    public static NTLMType2Message parse(byte[] header) {
        byte[] target = NTLMUtils.getSecurityBuffer(12, header);
        byte[] flags = Arrays.copyOfRange(header, 20, 24);
        byte[] challenge = Arrays.copyOfRange(header, 24, 32);
        byte[] context = Arrays.copyOfRange(header, 32, 40);
        TargetInformation targetInformation = new TargetInformation(NTLMUtils.getSecurityBuffer(40, header));
        byte[] version = Arrays.copyOfRange(header, 48, 56);
        return new NTLMType2Message(target, flags, challenge, context, targetInformation, version);
    }

}
