/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.ntlm;

/**
 * Class for constants used in NTLM.
 */
public final class NTLMConstants {

    /** Magic constant courtesy of Microsoft. */
    public static final byte[] CLIENTSIGNINGCONSTANT = "session key to client-to-server signing key magic constant\0"
            .getBytes(NTLMUtils.DEFAULT_CHARSET);
    /** Magic constant courtesy of Microsoft. */
    public static final byte[] CLIENTSEALINGCONSTANT = "session key to client-to-server sealing key magic constant\0"
            .getBytes(NTLMUtils.DEFAULT_CHARSET);
    /** Magic constant courtesy of Microsoft. */
    public static final byte[] SERVERSIGNINGCONSTANT = "session key to server-to-client signing key magic constant\0"
            .getBytes(NTLMUtils.DEFAULT_CHARSET);
    /** Magic constant courtesy of Microsoft. */
    public static final byte[] SERVERSEALINGCONSTANT = "session key to server-to-client sealing key magic constant\0"
            .getBytes(NTLMUtils.DEFAULT_CHARSET);
    /** Magic constant courtesy of Microsoft. It is a 4-byte number with value 1 in little endian notation. */
    public static final byte[] VERSION_NUMBER = { 0x01, 0x00, 0x00, 0x00 };
    /**
     * Size of the signature. In theory this can be any value; in practice it is 16. Similar to version, it is a 4-byte
     * number in little endian.
     */
    public static final byte[] SIGNATURE_SIZE = { 0x10, 0x00, 0x00, 0x00 };
    /** The size of the header that comes before the encrypted payload. */
    public static final int ENCRYPTED_HEADER_LENGTH = 20;
    /** This is the last plaintext section before the encrypted part. */
    public static final String CONTENT_TYPE_FOR_ENCRYPTED_PART = "application/octet-stream";
    /** This is the last plaintext section before the encrypted part. */
    public static final byte[] CONTENT_TYPE_FOR_ENCRYPTED_PART_AS_BYTES = CONTENT_TYPE_FOR_ENCRYPTED_PART
            .getBytes(NTLMUtils.DEFAULT_CHARSET);
    /** Constant for SPNEGO content-type. */
    public static final String CONTENT_TYPE_FOR_SPNEGO = "application/HTTP-SPNEGO-session-encrypted";
    /** Newline in http messages. */
    public static final String NEWLINE = "\r\n";
    /** Newline in http messages. */
    public static final byte[] NEWLINE_AS_BYTES = NEWLINE.getBytes(NTLMUtils.DEFAULT_CHARSET);
    /** Characters that go before (and after the last) boundary. */
    public static final String BOUNDARY_PREFIX = "--";
    /** Constant for charset. */
    public static final String CHARSET = "charset";
    /** Constant for horizontal tab. */
    public static final String HORIZONTAL_TAB = "\t";
    /** Constant for type. */
    public static final String TYPE = "type";
    /** Constant for length. */
    public static final String LENGTH = "Length";
    /** Constant for length. */
    public static final byte[] LENGTH_AS_BYTES = LENGTH.getBytes(NTLMUtils.DEFAULT_CHARSET);
    /** Constant for original content. */
    public static final String ORIGINAL_CONTENT = "OriginalContent";
    /** Constant for original content. */
    public static final byte[] ORIGINAL_CONTENT_AS_BYTES = ORIGINAL_CONTENT.getBytes(NTLMUtils.DEFAULT_CHARSET);
    /** Constant for content type to use in soap. */
    public static final String CONTENT_TYPE_FOR_SOAP = "application/soap+xml";
    /** Constant for boundary. */
    public static final String BOUNDARY = "boundary";
    /** Constant for protocol. */
    public static final String PROTOCOL = "protocol";
    /** Constant for multipart/encrypted. */
    public static final String CONTENT_TYPE_FOR_MULTIPART_ENCRYPTED = "multipart/encrypted";
    /** Constant for semicolon. */
    public static final String SEMICOLON = ";";
    /** Constant for equals. */
    public static final String EQUALS = "=";
    /** Constant for equals. */
    public static final byte[] EQUALS_AS_BYTES = EQUALS.getBytes(NTLMUtils.DEFAULT_CHARSET);

    /**
     * Private constructor.
     */
    private NTLMConstants() {

    }

}
