/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.ntlm;

import static com.iwave.ext.windows.winrm.ntlm.NTLMConstants.BOUNDARY_PREFIX;
import static com.iwave.ext.windows.winrm.ntlm.NTLMConstants.CONTENT_TYPE_FOR_ENCRYPTED_PART_AS_BYTES;
import static com.iwave.ext.windows.winrm.ntlm.NTLMConstants.NEWLINE_AS_BYTES;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHeaderElement;
import org.apache.http.message.BasicHeaderValueFormatter;
import org.apache.http.message.BasicLineParser;
import org.apache.http.message.BasicNameValuePair;

/**
 * This class is responsible for taking an HttpEntity with an encrypted payload and presenting it unencrypted. The format of
 * the message should be
 * 
 * <pre>
 * --Encrypted Boundary\r\n
 * \tContent-Type: application/HTTP-SPNEGO-session-encrypted\r\n
 * \tOriginalContent: type={type};charset={charset};Length={LENGTH}\r\n
 * --Encrypted Boundary\r\n
 * \tContent-Type: application/octet-stream\r\n
 * 20 bytes for a signature
 * {ENCRYPTED PAYLOAD OF SIZE {LENGTH}}
 * --Encrypted Boundary--
 * </pre>
 * 
 */
public class NTLMDecryptedEntity extends HttpEntityWrapper {

    /** The plaintext from the encrypted payload. */
    private byte[] plaintext;
    /** The length of the plaintext, as indicated by the header in the message. */
    private Integer length;
    /** The content type of the plaintext message. */
    private Header contentType;

    /**
     * This constructor decrypts the wrapped entity.
     * 
     * @param wrappedEntity
     *            the entity to decrypt
     * @param crypt
     *            the encryption object to use to decrypt the payload
     * @param boundary
     *            the boundary for the multi-part message
     */
    public NTLMDecryptedEntity(HttpEntity wrappedEntity, NTLMCrypt crypt, String boundary) {
        super(wrappedEntity);
        byte[] actualBoundary = (BOUNDARY_PREFIX + boundary).getBytes(NTLMUtils.DEFAULT_CHARSET);
        try {
            byte[] content = IOUtils.toByteArray(wrappedEntity.getContent());
            process(content, crypt, actualBoundary);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Processes the multipart message.
     * 
     * @param content
     *            the message
     * @param crypt
     *            the encryption mechanism
     * @param boundary
     *            the boundary of the multipart message
     * @throws UnrecognizedNTLMMessageException
     *             if something goes wrong
     */
    private void process(byte[] content, NTLMCrypt crypt, byte[] boundary) throws UnrecognizedNTLMMessageException {
        List<byte[]> parts = NTLMUtils.split(boundary, content);
        if (parts.size() != 4) {
            throw new RuntimeException("The multipart message is not well formed.");
        }
        processHeaders(parts.get(1));
        processPayload(parts.get(2), crypt);
    }

    /**
     * Processes the section of the multipart message that is headers.
     * 
     * @param headerBytes
     *            the header bytes
     */
    private void processHeaders(byte[] headerBytes) {
        try {
            int start = NTLMUtils.indexOf(NTLMConstants.ORIGINAL_CONTENT_AS_BYTES, headerBytes);
            int end = NTLMUtils.indexOf(NTLMConstants.NEWLINE_AS_BYTES, headerBytes, start);
            String type = null;
            String encoding = null;
            Header header = BasicLineParser.parseHeader(new String(Arrays.copyOfRange(headerBytes, start, end),
                    NTLMUtils.DEFAULT_CHARSET), null);
            for (HeaderElement element : header.getElements()) {
                if (element.getName().equals(NTLMConstants.TYPE)) {
                    type = element.getValue();
                }
                for (NameValuePair nvp : element.getParameters()) {
                    if (nvp.getName().equals(NTLMConstants.CHARSET)) {
                        encoding = nvp.getValue();
                    } else if (nvp.getName().equals(NTLMConstants.LENGTH)) {
                        length = Integer.parseInt(nvp.getValue());
                    }
                }
            }
            if (type == null || encoding == null || length == null) {
                throw new Exception("OriginalContent does not contain the necessary values. " + header.getValue());
            }
            contentType = buildContentTypeHeader(type, encoding);
        } catch (Exception e) {
            throw new RuntimeException("There was an error extracting values from the oringial content header.", e);
        }
    }

    /**
     * Builds the content type header that would have existed in a decrypted message.
     *
     * @param type
     *            the original content type
     * @param encoding
     *            the original charset
     * 
     * @return the new content type header
     */
    private Header buildContentTypeHeader(String type, String encoding) {
        NameValuePair[] nvps = new NameValuePair[] { new BasicNameValuePair(NTLMConstants.CHARSET, encoding), };
        BasicHeaderElement elem = new BasicHeaderElement(type, null, nvps);
        return new BasicHeader(HttpHeaders.CONTENT_TYPE, BasicHeaderValueFormatter.formatHeaderElement(elem, false, null));
    }

    /**
     * Process the payload from the multipart message.
     * 
     * @param payloadBytes
     *            the part of the message that is the payload
     * @param crypt
     *            the encryption mechanism
     * @throws UnrecognizedNTLMMessageException
     *             if something goes wrong
     */
    private void processPayload(byte[] payloadBytes, NTLMCrypt crypt) throws UnrecognizedNTLMMessageException {
        int start = NTLMUtils.indexOf(CONTENT_TYPE_FOR_ENCRYPTED_PART_AS_BYTES, payloadBytes)
                + CONTENT_TYPE_FOR_ENCRYPTED_PART_AS_BYTES.length + NEWLINE_AS_BYTES.length;
        plaintext = crypt.decryptAndVerifyPayload(Arrays.copyOfRange(payloadBytes, start, payloadBytes.length),
                (int) getContentLength());
    }

    @Override
    public InputStream getContent() {
        return new ByteArrayInputStream(plaintext);
    }

    @Override
    public long getContentLength() {
        return length;
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        outstream.write(plaintext);
    }

    @Override
    public Header getContentType() {
        if (contentType == null) {
            return super.getContentType();
        } else {
            return contentType;
        }
    }
}