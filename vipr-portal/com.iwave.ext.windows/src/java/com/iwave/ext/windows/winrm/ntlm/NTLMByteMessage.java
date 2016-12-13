/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.ntlm;

import java.util.ArrayList;
import java.util.List;

/**
 * This class exists to take care of the creation of an NTLM Header.
 */
public class NTLMByteMessage {

    /** The elements of the header. */
    private List<ByteArray> items;
    /** Our current position in the array. */
    private int position;
    /** The current offset in the data portion of the array. */
    private int offset;

    /**
     * Creates a new NTLMByteMessage.
     */
    public NTLMByteMessage() {
        items = new ArrayList<ByteArray>();
        position = 0;
        offset = 0;
    }

    /**
     * Adds a byte array to the header section of the message.
     * 
     * @param bytes
     *            the header to add
     */
    public void putHeader(byte[] bytes) {
        items.add(new HeaderByteArray(bytes));
    }

    /**
     * Adds a section to the data portion of the array.
     * 
     * @param bytes
     *            the data to add
     */
    public void putData(byte[] bytes) {
        items.add(new DataByteArray(bytes));
    }

    /**
     * Retrieves the byte message that was created.
     * 
     * @return the message
     */
    public byte[] getMsg() {
        int dataSize = 0;
        for (ByteArray b : items) {
            dataSize += b.getDataLength();
            offset += b.getHeaderLength();
        }
        int initialOffset = offset;
        byte[] msg = new byte[dataSize + offset];
        for (ByteArray b : items) {
            b.writeTo(msg);
        }
        if (position > initialOffset) {
            throw new RuntimeException("The header section of the message overflowed it's allowable size.");
        }
        return msg;
    }

    /**
     * Represents a section of the total message.
     * 
     * @author Jason Forand
     *
     */
    private abstract class ByteArray {
        /**
         * Retrieves the length of the header section of this component.
         * 
         * @return the length of the header
         */
        public abstract int getHeaderLength();

        /**
         * Retrieves the length of the data section of this component.
         * 
         * @return the length of the data
         */
        public abstract int getDataLength();

        /**
         * Writes the element to the given byte array.
         * 
         * @param array
         *            the array to write to
         */
        public abstract void writeTo(byte[] array);
    }

    /**
     * This element only has a header component.
     * 
     * @author Jason Forand
     *
     */
    private class HeaderByteArray extends ByteArray {

        /** The header bytes. */
        private byte[] bytes;

        /**
         * Creates a new header for the message.
         * 
         * @param bytes
         *            the bytes for the header
         */
        public HeaderByteArray(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public int getHeaderLength() {
            return bytes.length;
        }

        @Override
        public int getDataLength() {
            return 0;
        }

        @Override
        public void writeTo(byte[] array) {
            System.arraycopy(bytes, 0, array, position, bytes.length);
            position += bytes.length;
        }
    }

    /**
     * This element contributes to both the header section and the data section.
     * 
     * @author Jason Forand
     *
     */
    private class DataByteArray extends ByteArray {
        /** The bytes to write to the data section. */
        private byte[] bytes;
        /** The length of the security buffer that will be added to the header. */
        private static final int SECURITY_BUFFER_LENGTH = 8;

        /**
         * Creates a new data section for the message.
         * 
         * @param bytes
         *            the bytes in the data section
         */
        public DataByteArray(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public int getHeaderLength() {
            return SECURITY_BUFFER_LENGTH;
        }

        @Override
        public int getDataLength() {
            return bytes.length;
        }

        @Override
        public void writeTo(byte[] array) {
            new HeaderByteArray(NTLMUtils.createSecurityBuffer((short) bytes.length, offset)).writeTo(array);
            System.arraycopy(bytes, 0, array, offset, bytes.length);
            offset += bytes.length;
        }
    }

}
