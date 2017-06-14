/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.ntlm;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.codec.CharEncoding;

/**
 * A structure representing target information. https://msdn.microsoft.com/en-us/library/cc236646.aspx
 */
public class TargetInformation {

    /**
     * The different byte arrays in the information.
     */
    private Map<Short, byte[]> infos;
    /** Constant for the MsvAvNbComputerName. */
    public static final short MSV_AV_NB_COMPUTER_NAME = 1;
    /** Constant for the MsvAvNbDomainName. */
    public static final short MSV_AV_NB_DOMAIN_NAME = 2;
    /** Constant for the MsvAvDnsComputerName. */
    public static final short MSV_AV_DNS_COMPUTER_NAME = 3;
    /** Constant for the MsvAvDnsDomainName. */
    public static final short MSV_AV_DNS_DOMAIN_NAME = 4;
    /** Constant for the MsvAvDnsTreeName. */
    public static final short MSV_AV_DNS_TREE_NAME = 5;
    /** Constant for the MsvAvFlags. */
    public static final short MSV_AV_FLAGS = 6;
    /** Constant for the MsvAvTimestamp. */
    public static final short MSV_AV_TIMESTAMP = 7;
    /** Constant for the MsvAvSingleHost. */
    public static final short MSV_AV_SINGLE_HOST = 8;
    /** Constant for the MsvAvTargetName. */
    public static final short MSV_AV_TARGET_NAME = 9;
    /** Constant for the MsvChannelBindings. */
    public static final short MSV_CHANNEL_BINDINGS = 10;

    /**
     * Constructor for target information.
     * 
     * @param originalBytes
     *            the bytes of the information
     */
    public TargetInformation(byte[] originalBytes) {
        infos = new HashMap<Short, byte[]>();
        int index = 0;
        while (index < originalBytes.length) {
            short type = NTLMUtils.getShort(index, originalBytes);
            short length = NTLMUtils.getShort(index + 2, originalBytes);
            index += 4;
            if (type == 0) {
                // End of array
                break;
            } else {
                infos.put(type, Arrays.copyOfRange(originalBytes, index, index + length));
            }
            index += length;
        }
    }

    /**
     * Helper to build the target information byte array.
     * 
     * @param msg
     *            the byte message that is being built
     * @param bytes
     *            the bytes to add
     * @param num
     *            the type of the bytes
     */
    private void put(NTLMByteMessage msg, byte[] bytes, int num) {
        if (bytes != null) {
            msg.putHeader(NTLMUtils.convertShort((short) num));
            msg.putHeader(NTLMUtils.convertShort((short) bytes.length));
            msg.putHeader(bytes);
        }
    }

    /**
     * Builds the target information byte array.
     * 
     * @return the target information
     */
    public byte[] build() {
        NTLMByteMessage msg = new NTLMByteMessage();
        for (Entry<Short, byte[]> entry : infos.entrySet()) {
            put(msg, entry.getValue(), entry.getKey());
        }
        // Cap with 4 0's
        msg.putHeader(NTLMUtils.convertInt(0));
        return msg.getMsg();
    }

    /**
     * Put a section of the target information into the target information.
     * 
     * @param num
     *            the type of the section
     * @param buffer
     *            the data of the section
     */
    public void put(short num, byte[] buffer) {
        infos.put(num, buffer);
    }

    /**
     * Retrieves a section of the target information.
     * 
     * @param num
     *            the type of the section to retrieve
     * @return the section
     */
    public byte[] get(short num) {
        return infos.get(num);
    }

    @Override
    public String toString() {
        try {
            StringBuilder builder = new StringBuilder();
            for (Entry<Short, byte[]> entry : infos.entrySet()) {
                builder.append(entry.getKey()).append(" = ");
                if (entry.getKey() == MSV_AV_FLAGS) {
                    builder.append(NTLMUtils.getInt(0, infos.get(MSV_AV_FLAGS)));
                } else if (entry.getKey() == MSV_AV_TIMESTAMP) {
                    builder.append(NTLMUtils.fromMicrosoftTimestamp(infos.get(MSV_AV_TIMESTAMP)));
                } else {
                    builder.append(new String(entry.getValue(), CharEncoding.UTF_16LE));
                }
                builder.append("\n");
            }
            return builder.toString();
        } catch (Exception e) {
            // Guess we just don't get a to string on this one
        }
        return "";
    }

}
