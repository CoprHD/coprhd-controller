/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.authentication;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator.SignatureKeyType;

/**
 * Abstract authentication filter which has support for processing HMAC signatures
 */
public abstract class AbstractHMACAuthFilter extends AbstractAuthenticationFilter {
    private static final Logger _log = LoggerFactory.getLogger(AbstractHMACAuthFilter.class);
    public static final String INTERNODE_HMAC = "Internode-HMAC";
    public static final String INTERNODE_TIMESTAMP = "Internode-Timestamp";
    public static final String SIGNATURE_ALGO = "HmacSHA1";
    private InternalApiSignatureKeyGenerator _keyGenerator;

    /**
     * Set key generator
     * 
     * @see SignatureKeyGenerator
     */
    public void setKeyGenerator(InternalApiSignatureKeyGenerator keyGenerator) {
        _keyGenerator = keyGenerator;
    }

    /**
     * Verifies signature on the request using the specified signature key type
     * 
     * @param type specifies the type of key to use for verification (vdc or internal api)
     * @param req
     * @return true if the signature is good, false otherwise
     */
    protected boolean verifySignature(HttpServletRequest req, SignatureKeyType type) {
        // To Do - add more fields to signature
        StringBuilder buf = new StringBuilder(req.getPathInfo().toString().toLowerCase());
        if (req.getQueryString() != null) {
            buf.append("?" + req.getQueryString());
        }
        String timestamp = req.getHeader(INTERNODE_TIMESTAMP);
        if (timestamp != null && !timestamp.isEmpty()) {
            buf.append(req.getHeader(INTERNODE_TIMESTAMP));
        } else {
            return false;
        }
        _log.debug("buf: " + buf.toString());
        String headerSignature = req.getHeader(INTERNODE_HMAC);
        if (!trySignature(buf.toString(), headerSignature, type)) {
            _log.info("Failed with signature key type {}. Reloading cached keys and trying again", type.toString());
            _keyGenerator.loadKeys();
            return trySignature(buf.toString(), headerSignature, type);
        }
        return true;
    }

    /**
     * Attempts to validate signature of given type
     * 
     * @param buf the buffer to validate
     * @param headerSignature the header signature to compare to
     * @param type the type of key (vdc or internal api)
     * @return
     */
    private boolean trySignature(String buf, String headerSignature, SignatureKeyType type) {
        String signature = _keyGenerator.sign(buf.toString(), type);
        _log.debug("signature: " + (signature != null ? signature : "null"));
        _log.debug("headerSignature: " + (headerSignature != null ? headerSignature : "null"));
        if (StringUtils.isNotBlank(headerSignature) && StringUtils.isNotBlank(signature) &&
                headerSignature.equals(signature)) {
            return true;
        }
        return false;
    }

    /**
     * Verifies signature on the request using internal api key
     * 
     * @param req
     * @return true if the signature is good, false otherwise
     */
    protected boolean verifySignature(HttpServletRequest req) {
        return verifySignature(req, SignatureKeyType.INTERNAL_API);
    }
}
