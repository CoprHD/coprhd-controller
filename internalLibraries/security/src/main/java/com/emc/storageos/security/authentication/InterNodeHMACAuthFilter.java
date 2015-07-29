/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.authentication;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * HMAC authentication filter for inter-node internal apis
 */
public class InterNodeHMACAuthFilter extends AbstractHMACAuthFilter {
    @SuppressWarnings("unused")
    private static final Logger _log = LoggerFactory.getLogger(InterNodeHMACAuthFilter.class);
    public static final String INTERNAL_URI = "/internal/";

    // maintained here for backward compatibility
    public static final String INTERNODE_HMAC = AbstractHMACAuthFilter.INTERNODE_HMAC;
    public static final String INTERNODE_TIMESTAMP = AbstractHMACAuthFilter.INTERNODE_TIMESTAMP;
    public static final String SIGNATURE_ALGO = AbstractHMACAuthFilter.SIGNATURE_ALGO;

    @Override
    protected AbstractRequestWrapperFilter.AbstractRequestWrapper authenticate(
            final ServletRequest servletRequest) {
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        if (isInternalRequest(req)) {
            if (verifySignature(req)) {
                return new AbstractRequestWrapperFilter.AbstractRequestWrapper(req, null);
            } else {
                throw APIException.unauthorized
                        .unauthenticatedRequestUnsignedInternalRequest();
            }
        } else if (!req.isSecure()) {
            throw APIException.unauthorized.unauthenticatedRequestUseHTTPS();
        }
        return null;
    }

    /**
     * Determine if a request is intended for the internal (/internal/*) APIs
     * 
     * @param req an HTTP servlet request object
     * @return true if the URI pattern in the request matches the internal APIs
     */
    public static boolean isInternalRequest(HttpServletRequest req) {
        return ((req != null) && req.getRequestURI().contains(INTERNAL_URI));
    }
}
