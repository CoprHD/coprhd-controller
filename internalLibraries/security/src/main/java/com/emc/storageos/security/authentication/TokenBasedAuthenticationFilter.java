/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012-2013 EMC Corporation
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
import java.net.URI;
import java.net.URLEncoder;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.services.util.SecurityUtils;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.google.common.net.InetAddresses;


/**
 * Client side Authentication filter for the Bourne Token authentication mechanism  
 */
public class TokenBasedAuthenticationFilter extends AbstractAuthenticationFilter {
    private final Logger _logger = LoggerFactory.getLogger(TokenBasedAuthenticationFilter.class);
    private final String REQUESTING_COOKIES = RequestProcessingUtils.REQUESTING_COOKIES;

    @Autowired 
    private AuthSvcEndPointLocator _endpointLocator;
    
    private boolean _usingFormLogin;
    /**
     * Set usingFormLogin to redirect the service to form login page
     * @param value true or false
     */
    public void setUsingFormLogin(String value) {
        _usingFormLogin = Boolean.parseBoolean(value);
    }

    // Overriding doFilter even though it is in the base class.
    // Need a different logic to forward to authsvc when authenticate fails.
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
            _logger.debug("unauthorized request: serviceUrl = " + request.getRequestURI(), e);
            response.setStatus(toHTTPStatus(e));
            response.getOutputStream().print(toServiceErrorXml(e));
            response.setHeader("Content-Type", "application/xml");
            return;
        } catch (final InternalException e) {
            response.setStatus(toHTTPStatus(e));
            response.getOutputStream().print(toServiceErrorXml(e));
            response.setHeader("Content-Type", "application/xml");
            return;
        }

        if (reqWrapper != null) {
            // we are done, forward it to resource service
            forwardToService(servletRequest, servletResponse, reqWrapper);
        } else {
            // We need to go get a token from authsvc.
            forwardToAuthService(request, response);
        }
    }

    @Override
    protected AbstractRequestWrapper authenticate(ServletRequest servletRequest) {
        final StorageOSUser user = getStorageOSUserFromRequest(servletRequest, true);
        if (user != null) {
            // Token found and validated.  Proceed to the rest of the filter chain.
            return new AbstractRequestWrapper((HttpServletRequest)servletRequest, user);
        } else {
            _logger.debug("No token found in request.");
            return null;
        }
    }

    /**
     * Forward to Bourne authsvc
     * @param req
     * @param servletResponse
     * @throws java.io.IOException
     * @throws javax.servlet.ServletException
     */
    protected void forwardToAuthService(final HttpServletRequest req,
                                        final HttpServletResponse servletResponse)
            throws IOException, ServletException {

        boolean formLoginRequested = _usingFormLogin || RequestProcessingUtils.isRequestingQueryParam(req, RequestProcessingUtils.REQUESTING_FORMLOGIN);
        boolean cookiesRequested = RequestProcessingUtils.isRequestingQueryParam(req, RequestProcessingUtils.REQUESTING_COOKIES);
        boolean isRequestFromLB = RequestProcessingUtils.isRequestFromLoadBalancer(req);
        URI endpoint = null;

        if (isRequestFromLB) {
            endpoint = URI.create(req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort());
        }
        else {
            try {
                endpoint = _endpointLocator.getAnEndpoint();
            } catch (InternalException e) {
                servletResponse.sendError(toHTTPStatus(e), toServiceErrorXml(e));
            }
        }

        StringBuilder redirectURL = new StringBuilder(endpoint.toString());
        if (cookiesRequested || formLoginRequested || !InetAddresses.isInetAddress(endpoint.getHost())) {
            // ok, then, keep them on the same node
            redirectURL = RequestProcessingUtils.getOnNodeAuthsvcRedirectURL(req, endpoint);
        } 

        if( formLoginRequested ) {
            redirectURL.append("/formlogin?");
        } else {
            redirectURL.append("/login?");
        }
        StringBuilder serviceURL = new StringBuilder(req.getRequestURL().toString());
        String queryString = SecurityUtils.stripXSS(RequestProcessingUtils.removeFromQueryString(req.getQueryString(), REQUESTING_COOKIES));

        if (queryString != null && !queryString.isEmpty()) {
            serviceURL.append("?" + queryString);
        }
        redirectURL.append("service=");
        redirectURL.append(URLEncoder.encode(serviceURL.toString(), "UTF-8"));
        // adding requesting cookies if needed
        if (cookiesRequested) {
            redirectURL.append(String.format("&%s=true",REQUESTING_COOKIES));
        }

        // CHECK - if we are already back from redirect or we have a non-GET request
        // we don't redirect in these cases
        boolean redirectLoop = (req.getQueryString() != null &&
                req.getQueryString().contains(RequestProcessingUtils.REDIRECT_FROM_AUTHSVC));
        if (redirectLoop || !req.getMethod().equals(HttpMethod.GET)) {
            servletResponse.setHeader(HttpHeaders.LOCATION, redirectURL.toString());
            _logger.debug("sending unauthorized status code (401), Location={}" + redirectURL);
            servletResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    (redirectLoop) ? "using cookies? retry using \"using-cookies\" query parameter" :
                            "Non GET Unauthenticated request: authenticate using " + redirectURL);
        } else {
            _logger.info("redirecting request for authentication: url: {}", redirectURL.toString());
            servletResponse.sendRedirect(redirectURL.toString());
        }
    }

}
