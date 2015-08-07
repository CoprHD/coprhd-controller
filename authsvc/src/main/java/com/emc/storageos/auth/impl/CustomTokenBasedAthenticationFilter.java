/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.impl;

import java.io.IOException;

import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authentication.TokenBasedAuthenticationFilter;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom token based filter for authsvc. Special case for /user/; unauthenticated request to that URI
 * get forwarded with 302. Others go through authsvc to get processed there (new tokens etc.)
 */
public class CustomTokenBasedAthenticationFilter extends TokenBasedAuthenticationFilter {

    private final Logger _log = LoggerFactory.getLogger(CustomTokenBasedAthenticationFilter.class);

    @Override
    protected AbstractRequestWrapper authenticate(final ServletRequest servletRequest) {
        final StorageOSUser user = getStorageOSUserFromRequest(servletRequest, true);
        return new AbstractRequestWrapper((HttpServletRequest) servletRequest, user);
    }

    // This filter will forward to authsvc only if the resource is /user and there is no
    // authenticated context. Else, just let it through to authsvc (it will know what to
    // to do)
    @Override
    public void doFilter(final ServletRequest servletRequest,
            final ServletResponse servletResponse,
            final FilterChain filterChain) throws IOException, ServletException {
        final HttpServletResponse response = (HttpServletResponse) servletResponse;
        final HttpServletRequest request = (HttpServletRequest) servletRequest;
        AbstractRequestWrapper reqWrapper = null;
        try {
            reqWrapper = authenticate(servletRequest);
        } catch (APIException e) {
            _log.debug("unauthorized request: serviceUrl = " + request.getRequestURI(), e);
            response.sendError(toHTTPStatus(e), toServiceErrorXml(e));
            return;
        } catch (final InternalException e) {
            response.sendError(toHTTPStatus(e), toServiceErrorXml(e));
            return;
        }
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        String uri = req.getRequestURI();
        if (reqWrapper.getUserPrincipal() == null && uri.toLowerCase().startsWith("/user/")) {
            forwardToAuthService(request, response);
        } else {
            forwardToService(servletRequest, servletResponse, reqWrapper);
        }
    }
}
