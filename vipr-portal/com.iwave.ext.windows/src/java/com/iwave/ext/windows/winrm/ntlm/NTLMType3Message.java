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
 * Class representing an NTLM Type 3 Message. The format of the message is as follows.
 * 
 * <pre>
 *      Description             Content     
 *   0  NTLMSSP Signature       Null-terminated ASCII "NTLMSSP" (0x4e544c4d53535000)
 *   8  NTLM Message Type       long (0x03000000)
 *  12  LM/LMv2 Response        security buffer
 *  20  NTLM/NTLMv2 Response    security buffer
 *  28  Target Name             security buffer
 *  36  User Name               security buffer
 *  44  Workstation Name        security buffer
 *  52 Session Key              security buffer
 *  60 Flags                    long
 *  64 OS Version Structure     8 bytes
 *  72 MIC                      16 bytes
 *  88 start of data block
 * </pre>
 * 
 * @author Jason Forand
 *
 */

public class NTLMType3Message extends NTLMMessage {

    /**
     * The logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(NTLMType3Message.class);

    /** The lm response byte array. */
    private byte[] lmresp;
    /** The ntlm response byte array. */
    private byte[] ntlmresp;
    /** The target name byte array. */
    private byte[] targetname;
    /** The username byte array. */
    private byte[] username;
    /** The workstation name byte array. */
    private byte[] workstationname;
    /** The session key byte array. */
    private byte[] sessionkey;
    /** The type of this NTLM message. */
    public static final int TYPE = 3;
    /** The message integrity check. */
    private byte[] mic;

    /**
     * 
     * @param lmresp
     *            the lmresp
     * @param ntlmresp
     *            the ntlmresp
     * @param targetname
     *            the targetname
     * @param username
     *            the username
     * @param workstationname
     *            the workstationname
     * @param sessionkey
     *            the sessionkey
     * @param flags
     *            the flags
     * @param version
     *            the version
     * @param mic
     *            the message integrity check
     */
    public NTLMType3Message(byte[] lmresp, byte[] ntlmresp, byte[] targetname, byte[] username, byte[] workstationname,
            byte[] sessionkey, byte[] flags, byte[] version, byte[] mic) {
        super(flags, version);
        this.lmresp = lmresp;
        this.ntlmresp = ntlmresp;
        this.targetname = targetname;
        this.username = username;
        this.workstationname = workstationname;
        this.sessionkey = sessionkey;
        this.mic = mic;
    }

    /**
     * @return the lmresp
     */
    public byte[] getLmresp() {
        return lmresp;
    }

    /**
     * @param lmresp
     *            the lmresp to set
     */
    public void setLmresp(byte[] lmresp) {
        this.lmresp = lmresp;
    }

    /**
     * @return the ntlmresp
     */
    public byte[] getNtlmresp() {
        return ntlmresp;
    }

    /**
     * @param ntlmresp
     *            the ntlmresp to set
     */
    public void setNtlmresp(byte[] ntlmresp) {
        this.ntlmresp = ntlmresp;
    }

    /**
     * @return the targetname
     */
    public byte[] getTargetname() {
        return targetname;
    }

    /**
     * @param targetname
     *            the targetname to set
     */
    public void setTargetname(byte[] targetname) {
        this.targetname = targetname;
    }

    /**
     * @return the username
     */
    public byte[] getUsername() {
        return username;
    }

    /**
     * @param username
     *            the username to set
     */
    public void setUsername(byte[] username) {
        this.username = username;
    }

    /**
     * @return the workstationname
     */
    public byte[] getWorkstationname() {
        return workstationname;
    }

    /**
     * @param workstationname
     *            the workstationname to set
     */
    public void setWorkstationname(byte[] workstationname) {
        this.workstationname = workstationname;
    }

    /**
     * @return the sessionkey
     */
    public byte[] getSessionkey() {
        return sessionkey;
    }

    /**
     * @param sessionkey
     *            the sessionkey to set
     */
    public void setSessionkey(byte[] sessionkey) {
        this.sessionkey = sessionkey;
    }

    /**
     * Returns the client challenge to the server. The location of the nonce can be found in the documentation.
     * 
     * @return the client challenge
     */
    public byte[] getNonce() {
        // The nonce is the first 8 bytes of the lmresp... check the doc
        return Arrays.copyOfRange(lmresp, 0, 8);
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
        builder.append("TYPE 3\n");
        try {
            builder.append("TARGET: ").append(new String(targetname, CharEncoding.UTF_16LE)).append("\n");
            builder.append("WORKSTATION: ").append(new String(workstationname, CharEncoding.UTF_16LE)).append("\n");
            builder.append("USER: ").append(new String(username, CharEncoding.UTF_16LE)).append("\n");
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
        message.putHeader(NTLMUtils.convertInt(3));
        message.putData(lmresp);
        message.putData(ntlmresp);
        message.putData(targetname);
        message.putData(username);
        message.putData(workstationname);
        message.putData(sessionkey);
        message.putHeader(getFlags());
        message.putHeader(getVersion());
        message.putHeader(mic);

        return message.getMsg();
    }

    /**
     * Retrieve the mic from the type 3 message.
     * 
     * @return the mic
     */
    public byte[] getMic() {
        return mic;
    }

    /**
     * Sets the mic in the type 3 message.
     * 
     * @param mic
     *            the mic
     */
    public void setMic(byte[] mic) {
        this.mic = mic;
    }

    /**
     * Parses a byte header and creates a type 3 message out of it.
     * 
     * @param header
     *            the byte header
     * @return the created message
     */
    public static NTLMType3Message parse(byte[] header) {
        byte[] lmresp = NTLMUtils.getSecurityBuffer(12, header);
        byte[] ntlmresp = NTLMUtils.getSecurityBuffer(20, header);
        byte[] targetname = NTLMUtils.getSecurityBuffer(28, header);
        byte[] username = NTLMUtils.getSecurityBuffer(36, header);
        byte[] workstationname = NTLMUtils.getSecurityBuffer(44, header);
        byte[] sessionkey = NTLMUtils.getSecurityBuffer(52, header);
        byte[] flags = Arrays.copyOfRange(header, 60, 64);
        byte[] version = Arrays.copyOfRange(header, 64, 72);
        byte[] mic = Arrays.copyOfRange(header, 72, 88);
        return new NTLMType3Message(lmresp, ntlmresp, targetname, username, workstationname, sessionkey, flags, version,
                mic);
    }
}
