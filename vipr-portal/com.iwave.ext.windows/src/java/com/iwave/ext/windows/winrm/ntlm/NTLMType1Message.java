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
 * Class representing an NTLM Type 1 Message. The format of the message is as follows.
 * 
 * <pre>
 *              Description                     Content     
 *   0          NTLMSSP Signature               Null-terminated ASCII "NTLMSSP" (0x4e544c4d53535000)
 *   8          NTLM Message Type               long (0x01000000)
 *  12          Flags                           long
 * (16)         Supplied Domain (Optional)      security buffer
 * (24)         Supplied Workstation (Optional) security buffer
 * (32)         OS Version Structure (Optional) 8 bytes
 * (32) (40)    start of data block (if required)
 * </pre>
 * 
 * @author Jason Forand
 *
 */
public class NTLMType1Message extends NTLMMessage {

    /**
     * The logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(NTLMType1Message.class);

    /** byte array containing the domain. */
    private byte[] domain;
    /** byte array containing the workstation. */
    private byte[] workstation;

    /** The type of this NTLM message. */
    public static final int TYPE = 1;

    /**
     * 
     * @param flags
     *            the flags
     * @param domain
     *            the domain
     * @param workstation
     *            the workstation
     * @param version
     *            the version
     */
    public NTLMType1Message(byte[] flags, byte[] domain, byte[] workstation, byte[] version) {
        super(flags, version);
        this.domain = domain;
        this.workstation = workstation;
    }

    /**
     * @return the domain
     */
    public byte[] getDomain() {
        return domain;
    }

    /**
     * @param domain
     *            the domain to set
     */
    public void setDomain(byte[] domain) {
        this.domain = domain;
    }

    /**
     * @return the workstation
     */
    public byte[] getWorkstation() {
        return workstation;
    }

    /**
     * @param workstation
     *            the workstation to set
     */
    public void setWorkstation(byte[] workstation) {
        this.workstation = workstation;
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
        builder.append("TYPE 1\n");
        builder.append(getFlagsAsString());
        try {
            builder.append("DOMAIN:").append(new String(domain, CharEncoding.UTF_16LE));
            builder.append("WORKSTATION:").append(new String(workstation, CharEncoding.UTF_16LE));
        } catch (Exception e) {
            LOG.error("Impossible encoding exception.", e);
        }
        builder.append(getVersionAsString());
        return builder.toString();
    }

    @Override
    public byte[] toHeaderBytes() {
        NTLMByteMessage message = new NTLMByteMessage();

        message.putHeader(NTLMSSP_BYTES);
        message.putHeader(NTLMUtils.convertInt(1));
        message.putHeader(getFlags());
        message.putData(domain);
        message.putData(workstation);
        message.putHeader(getVersion());

        return message.getMsg();
    }

    /**
     * Parses a byte header and creates a type 1 message out of it.
     * 
     * @param header
     *            the byte header
     * @return the created message
     */
    public static NTLMType1Message parse(byte[] header) {
        byte[] flags = Arrays.copyOfRange(header, 12, 16);
        byte[] domain = NTLMUtils.getSecurityBuffer(16, header);
        byte[] workstation = NTLMUtils.getSecurityBuffer(24, header);
        byte[] version = Arrays.copyOfRange(header, 32, 40);
        return new NTLMType1Message(flags, domain, workstation, version);
    }

}
