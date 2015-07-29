/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.geo.service.authentication;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.security.authentication.AbstractHMACAuthFilter;
import com.emc.storageos.security.authentication.AbstractRequestWrapperFilter;
import com.emc.storageos.security.authentication.InternalApiSignatureKeyGenerator.SignatureKeyType;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * HMAC authentication filter for inter-VDC APIs
 * 
 * This filter is terminal - it either accepts a request as
 * a valid signed inter-VDC request or rejects it. It will
 * never delegate to a following filter.
 */
public class InterVDCHMACAuthFilter extends AbstractHMACAuthFilter {
    @SuppressWarnings("unused")
    private static final Logger _log = LoggerFactory
            .getLogger(InterVDCHMACAuthFilter.class);
    public static final String INTERVDC_URI = "/intervdc/";

    @Override
    protected AbstractRequestWrapperFilter.AbstractRequestWrapper authenticate(
            final ServletRequest servletRequest) {
        HttpServletRequest req = (HttpServletRequest) servletRequest;

        if (isInterVDCRequest(req) && verifySignature(req, SignatureKeyType.INTERVDC_API)) {
            return new AbstractRequestWrapperFilter.AbstractRequestWrapper(req, null);
        } else {
            throw APIException.unauthorized.unauthenticatedRequestUnsignedInterVDCRequest();
        }
    }

    /**
     * Determine if a request is intended for the inter-vdc (/inter-vdc/*) APIs
     * 
     * @param req an HTTP servlet request object
     * @return true if the URI pattern in the request matches the inter-vdc APIs
     */
    public static boolean isInterVDCRequest(HttpServletRequest req) {
        return ((req != null) && req.getRequestURI().contains(INTERVDC_URI));
    }
}
