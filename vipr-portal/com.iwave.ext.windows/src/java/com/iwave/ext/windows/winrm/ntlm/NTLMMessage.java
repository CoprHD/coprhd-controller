/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.ntlm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.iwave.ext.windows.winrm.Pair;

/**
 * This class contains the common elements of an NTLM message. Additionally it holds the functionality to convert from a byte
 * array header to an ntlm message and vice-versa. One of the things that is important to note is that every value in the
 * arrays is in little endian format.
 *
 */
public abstract class NTLMMessage {

    /** Constant for the beginning of the header. */
    public static final String NTLMSSP = "NTLMSSP\0";

    /** Constant in bytes. */
    public static final byte[] NTLMSSP_BYTES = NTLMSSP.getBytes(NTLMUtils.DEFAULT_CHARSET);

    /** A list of the flags used by the NTLM protocol. */
    @SuppressWarnings("serial")
    private static final List<Pair<Integer, String>> FLAGS = new ArrayList<Pair<Integer, String>>() {
        {
            add(new Pair<Integer, String>(1, "NEGOTIATE_UNICODE"));
            add(new Pair<Integer, String>(1 << 1, "NEGOTIATE_OEM"));
            add(new Pair<Integer, String>(1 << 2, "REQUEST_TARGET"));
            add(new Pair<Integer, String>(1 << 3, "UNKNOWN_1"));
            add(new Pair<Integer, String>(1 << 4, "NEGOTIATE_SIGN"));
            add(new Pair<Integer, String>(1 << 5, "NEGOTIATE_SEAL"));
            add(new Pair<Integer, String>(1 << 6, "NEGOTIATE_DATAGRAM_STYLE"));
            add(new Pair<Integer, String>(1 << 7, "NEGOTITATE_LAN_MANAGER_KEY"));
            add(new Pair<Integer, String>(1 << 8, "NEGOTIATE_NETWARE"));
            add(new Pair<Integer, String>(1 << 9, "NEGOTIATE_NTLM"));
            add(new Pair<Integer, String>(1 << 10, "UNKNOWN_2"));
            add(new Pair<Integer, String>(1 << 11, "NEGOTIATE_ANONYMOUS"));
            add(new Pair<Integer, String>(1 << 12, "NEGOTIATE_DOMAIN_SUPPLIED"));
            add(new Pair<Integer, String>(1 << 13, "NEGOTIATE_WORKSTATION_SUPPLIED"));
            add(new Pair<Integer, String>(1 << 14, "NEGOTIATE_LOCAL_CALL"));
            add(new Pair<Integer, String>(1 << 15, "NEGOTIATE_ALWAYS_SIGN"));
            add(new Pair<Integer, String>(1 << 16, "TARGET_TYPE_DOMAIN"));
            add(new Pair<Integer, String>(1 << 17, "TARGET_TYPE_SERVER"));
            add(new Pair<Integer, String>(1 << 18, "TARGET_TYPE_SHARE"));
            add(new Pair<Integer, String>(1 << 19, "NEGOTIATE_NTLM2_KEY"));
            add(new Pair<Integer, String>(1 << 20, "REQUEST_INIT_RESPONSE"));
            add(new Pair<Integer, String>(1 << 21, "REQUEST_ACCEPT_RESPONSE"));
            add(new Pair<Integer, String>(1 << 22, "REQUEST_NON_NT_SESSION_KEY"));
            add(new Pair<Integer, String>(1 << 23, "NEGOTIATE_TARGET_INFO"));
            add(new Pair<Integer, String>(1 << 24, "UNKNOWN_3"));
            add(new Pair<Integer, String>(1 << 25, "UNKNOWN_4"));
            add(new Pair<Integer, String>(1 << 26, "UNKNOWN_5"));
            add(new Pair<Integer, String>(1 << 27, "UNKNOWN_6"));
            add(new Pair<Integer, String>(1 << 28, "UNKNOWN_7"));
            add(new Pair<Integer, String>(1 << 29, "NEGOTIATE_128"));
            add(new Pair<Integer, String>(1 << 30, "NEGOTIATE_KEY_EXCHANGE"));
            add(new Pair<Integer, String>(1 << 31, "NEGOTIATE_56"));
        }
    };

    /**
     * Byte container for the version. It had the following internal structure.
     * 
     * <pre>
     *      Description             Content
     * 0    Major Version Number    1 byte
     * 1    Minor Version Number    1 byte
     * 2    Build Number            short
     * 4    Unknown                 0x0000000f
     * </pre>
     */
    private byte[] version;

    /**
     * Byte container for the flags. There are 32 different flags, you can find them in NTLMUtils, or by reading the
     * documentation.
     */
    private byte[] flags;

    /**
     * @return the version
     */
    public byte[] getVersion() {
        return version;
    }

    /**
     * @param version
     *            the version to set
     */
    public void setVersion(byte[] version) {
        this.version = version;
    }

    /**
     * @return the flags
     */
    public byte[] getFlags() {
        return flags;
    }

    /**
     * @param flags
     *            the flags to set
     */
    public void setFlags(byte[] flags) {
        this.flags = flags;
    }

    /**
     * Turns a flag on.
     * 
     * @param flag
     *            the flag to turn on
     */
    public void addFlag(int flag) {
        int flagAsInt = ByteBuffer.wrap(flags).order(ByteOrder.LITTLE_ENDIAN).getInt();
        flagAsInt = flagAsInt | flag;
        flags = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(flagAsInt).array();
    }

    /**
     * Checks if the flag is present.
     * 
     * @param flag
     *            the flag to check
     * @return true if it's turned on
     */
    public boolean hasFlag(int flag) {
        return (ByteBuffer.wrap(flags).order(ByteOrder.LITTLE_ENDIAN).getInt() & flag) == flag;
    }

    /**
     * 
     * @param flags
     *            the flags of the message
     * @param version
     *            the version of the host sending the message
     */
    public NTLMMessage(byte[] flags, byte[] version) {
        this.flags = flags;
        this.version = version;
    }

    /**
     * Version in human readable format.
     * 
     * @return the pretty printed string.
     */
    public String getVersionAsString() {
        return version[0] + "." + version[1] + " build " + NTLMUtils.getShort(2, version);
    }

    /**
     * Flags in human readable format.
     * 
     * @return the readable flags
     */
    public String getFlagsAsString() {
        StringBuilder builder = new StringBuilder();
        int flagAsInt = ByteBuffer.wrap(flags).order(ByteOrder.LITTLE_ENDIAN).getInt();
        for (Pair<Integer, String> flag : FLAGS) {
            if ((flag.getFirstElement() & flagAsInt) == flag.getFirstElement()) {
                builder.append(flag.getSecondElement()).append("\n");
            }
        }
        return builder.toString();
    }

    /**
     * Parses a header that has already been base64 decoded and returns an NTLMMessage corresponding to that header.
     * 
     * @param header
     *            the base64 decoded header
     * @return the NTLMMessage
     * @throws Exception
     *             if something goes wrong
     */
    public static NTLMMessage parse(byte[] header) throws Exception {
        if (!Arrays.equals(NTLMSSP_BYTES, Arrays.copyOfRange(header, 0, 8))) {
            throw new Exception("This is not an NTLM header");
        }
        int type = NTLMUtils.getInt(8, header);
        if (type == 1) {
            return NTLMType1Message.parse(header);
        } else if (type == 2) {
            return NTLMType2Message.parse(header);
        } else if (type == 3) {
            return NTLMType3Message.parse(header);
        } else {
            throw new Exception("Message is of type " + type + " and is not supported");
        }
    }

    /**
     * Transform the message into the byte array that will be put into an HTTP header.
     * 
     * @return the byte array
     */
    public abstract byte[] toHeaderBytes();

    /**
     * Retrieve the type of the message.
     * 
     * @return the type of the mesage
     */
    public abstract int getMessageType();

}
