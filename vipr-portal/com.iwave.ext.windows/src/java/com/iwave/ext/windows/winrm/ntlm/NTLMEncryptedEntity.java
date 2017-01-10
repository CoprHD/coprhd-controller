/*
 * Copyright (c) 2016 Dell EMC Software
 * All Rights Reserved
 */

package com.iwave.ext.windows.winrm.ntlm;

import static com.iwave.ext.windows.winrm.ntlm.NTLMConstants.BOUNDARY_PREFIX;
import static com.iwave.ext.windows.winrm.ntlm.NTLMConstants.CHARSET;
import static com.iwave.ext.windows.winrm.ntlm.NTLMConstants.CONTENT_TYPE_FOR_ENCRYPTED_PART;
import static com.iwave.ext.windows.winrm.ntlm.NTLMConstants.CONTENT_TYPE_FOR_SOAP;
import static com.iwave.ext.windows.winrm.ntlm.NTLMConstants.CONTENT_TYPE_FOR_SPNEGO;
import static com.iwave.ext.windows.winrm.ntlm.NTLMConstants.EQUALS;
import static com.iwave.ext.windows.winrm.ntlm.NTLMConstants.HORIZONTAL_TAB;
import static com.iwave.ext.windows.winrm.ntlm.NTLMConstants.LENGTH;
import static com.iwave.ext.windows.winrm.ntlm.NTLMConstants.NEWLINE;
import static com.iwave.ext.windows.winrm.ntlm.NTLMConstants.ORIGINAL_CONTENT;
import static com.iwave.ext.windows.winrm.ntlm.NTLMConstants.SEMICOLON;
import static com.iwave.ext.windows.winrm.ntlm.NTLMConstants.TYPE;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.CharEncoding;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicLineFormatter;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for taking an HttpEntity, encrypting it, and then presenting a new HttpEntity which follows the
 * NTLM specification. https://msdn.microsoft.com/en-us/library/cc251578.aspx
 * 
 * <pre>
 * --Encrypted Boundary\r\n
 * \tContent-Type: application/HTTP-SPNEGO-session-encrypted\r\n
 * \tOriginalContent: type=application/soap+xml;charset={ENCODING};Length={LENGTH}\r\n
 * --Encrypted Boundary\r\n
 * \tContent-Type: application/octet-stream\r\n
 * 20 bytes for a signature
 * {ENCRYPTED PAYLOAD OF SIZE {LENGTH}}
 * --Encrypted Boundary--
 * </pre>
 *
 */
public class NTLMEncryptedEntity extends HttpEntityWrapper {

    /**
     * The logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(NTLMEncryptedEntity.class);

    /** The encrypted content to send. */
    private byte[] encryptedContent;

    /** Constant for content type header. */
    private static final String CONTENT_TYPE = BasicLineFormatter.INSTANCE.formatHeader(null,
            new BasicHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_FOR_SPNEGO)).toString();

    /** Constant for encrypted content type header. */
    private static final String ENCRYPTED_CONTENT_TYPE = BasicLineFormatter.INSTANCE.formatHeader(null,
            new BasicHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_FOR_ENCRYPTED_PART)).toString();

    /**
     * Wraps and encrypts an http entity for transmission.
     * 
     * @param wrappedEntity
     *            the entity to wrap
     * @param crypt
     *            the encryption to use
     * @param boundary
     *            the boundary to use for the multipart message
     */
    public NTLMEncryptedEntity(HttpEntity wrappedEntity, NTLMCrypt crypt, String boundary) {
        super(wrappedEntity);
        try {
            byte[] content = IOUtils.toByteArray(wrappedEntity.getContent());
            StringBuilder builder = new StringBuilder();
            // Add a boundary
            builder.append(BOUNDARY_PREFIX);
            builder.append(boundary);
            builder.append(NEWLINE);
            // Add the content-type header
            builder.append(HORIZONTAL_TAB);
            builder.append(CONTENT_TYPE);
            builder.append(NEWLINE);
            // Add the original content header
            builder.append(HORIZONTAL_TAB);
            builder.append(BasicLineFormatter.INSTANCE.formatHeader(
                    null,
                    buildOriginalContentHeader(CONTENT_TYPE_FOR_SOAP, getEncoding(wrappedEntity),
                            Integer.toString(content.length)))
                    .toString());
            builder.append(NEWLINE);
            // Add another boundary
            builder.append(BOUNDARY_PREFIX);
            builder.append(boundary);
            builder.append(NEWLINE);
            // Add the encrypted header
            builder.append(HORIZONTAL_TAB);
            builder.append(ENCRYPTED_CONTENT_TYPE);
            builder.append(NEWLINE);
            // Concatenate everything together and add a boundary at the end
            encryptedContent = NTLMUtils.concat(builder.toString().getBytes(NTLMUtils.DEFAULT_CHARSET),
                    crypt.encryptAndSignPayload(content),
                    (BOUNDARY_PREFIX + boundary + BOUNDARY_PREFIX + NEWLINE).getBytes(NTLMUtils.DEFAULT_CHARSET));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Builds OriginalContent: type={type};charset={charset};Length=length. Explicitly with no spaces after semi colons or it
     * breaks.
     * 
     * @param type
     *            the type
     * @param charset
     *            the charset
     * @param length
     *            the length
     * @return OriginalContent: type={type};charset={charset};Length=length
     */
    private Header buildOriginalContentHeader(String type, String charset, String length) {
        NameValuePair[] nvps = new NameValuePair[] { new BasicNameValuePair(TYPE, type),
                new BasicNameValuePair(CHARSET, charset), new BasicNameValuePair(LENGTH, length) };
        // We can't use the apache header formatters because they add spaces after the semi-colons, and the NTLM
        // specification forbids it (it blows up when attempting communication). As a result, we need to handcraft the header
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < nvps.length; i++) {
            if (i > 0) {
                value.append(SEMICOLON);
            }
            value.append(nvps[i].getName()).append(EQUALS).append(nvps[i].getValue());
        }
        return new BasicHeader(ORIGINAL_CONTENT, value.toString());
    }

    /**
     * Retrieves the character encoding from the entity.
     * 
     * @param entity
     *            the entity
     * @return the encoding
     */
    private String getEncoding(HttpEntity entity) {
        String encoding = null;
        Header header = wrappedEntity.getContentType();
        if (header != null) {
            for (HeaderElement he : header.getElements()) {
                for (NameValuePair nvp : he.getParameters()) {
                    if (nvp.getName().equals(CHARSET)) {
                        encoding = nvp.getValue();
                    }
                }
            }
        }
        // The encoding must be UTF-16 or UTF-8 as specified by the NTLM spec.
        if (CharEncoding.UTF_16.equals(encoding) || CharEncoding.UTF_8.equals(encoding)) {
            return encoding;
        } else {
            LOG.warn(encoding + " is not a valid character set for NTLM communcation.");
            return NTLMUtils.DEFAULT_CHARSET.displayName();
        }
    }

    @Override
    public InputStream getContent() {
        return new ByteArrayInputStream(encryptedContent);
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        outstream.write(encryptedContent);
    }

    @Override
    public long getContentLength() {
        return encryptedContent.length;
    }

}