/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.authentication;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.mappers.ServiceCodeExceptionMapper;

/**
 * Abstract class for all other authentications we will need to support
 */
public abstract class AbstractAuthenticationFilter extends AbstractRequestWrapperFilter {
    private static final Logger _log = LoggerFactory.getLogger(AbstractAuthenticationFilter.class);

    /**
     * Forward the request to resource handler, we are done authenticating it
     * 
     * @param servletRequest
     * @param servletResponse
     * @param reqWrapper
     * @throws IOException
     * @throws ServletException
     */
    protected void forwardToService(final ServletRequest servletRequest,
            final ServletResponse servletResponse,
            final AbstractRequestWrapper reqWrapper)
            throws IOException, ServletException {
        try {
            HttpServletRequest req = (HttpServletRequest) servletRequest;
            servletRequest.getRequestDispatcher(req.getRequestURI()).forward(reqWrapper, servletResponse);
        } catch (WebApplicationException e) {
            if (ServiceCodeExceptionMapper.isStackTracePrinted(e)) {
                _log.warn("caught WebApplicationException", e);
            } else {
                _log.warn("caught WebApplicationException: {}", e.getMessage());
            }
            HttpServletResponse reponse = (HttpServletResponse) servletResponse;
            reponse.sendError(toHTTPStatus(e), toServiceErrorXml(e));
        }
    }

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
        if (reqWrapper != null) {
            // we are done, forward it to resource service
            forwardToService(servletRequest, servletResponse, reqWrapper);
        } else {
            // not mine, forward it on to the next filter
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }
}
