/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
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
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.google.common.net.InetAddresses;

/**
 * Logout request handler, redirects the request to authsvc
 */
public class LogoutHandlingFilter extends AbstractRequestWrapperFilter {
    private final Logger _logger = LoggerFactory.getLogger(LogoutHandlingFilter.class);
    final private String LOGOUT_URI = "/logout";
    final private String _regEx = "^" + LOGOUT_URI + "(\\.xml|\\.json)?$";
    final private Pattern _pattern = Pattern.compile(_regEx, Pattern.CASE_INSENSITIVE);

    @Autowired
    protected AuthSvcEndPointLocator _endpointLocator;

    @Override
    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse,
                         final FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) servletRequest;
        if (!findLogoutPattern(req.getRequestURI())) {
            filterChain.doFilter(servletRequest, servletResponse);
        } else {
            // logout request, handle here
            URI endpoint = null;
            try {
                endpoint = _endpointLocator.getAnEndpoint();
            } catch (SecurityException e) {
                final HttpServletResponse response = (HttpServletResponse) servletResponse;
                response.sendError(toHTTPStatus(e), toServiceErrorXml(e));
            }
            StringBuilder redirectURL = new StringBuilder(endpoint.toString());
            if (!InetAddresses.isInetAddress(endpoint.getHost()) ||
                    RequestProcessingUtils.getTokenFromCookie(req) != null){
                // ok, then, keep them on the same node
                redirectURL = RequestProcessingUtils.getOnNodeAuthsvcRedirectURL(req, endpoint);
            }
            redirectURL.append("/logout");
            String queryString = req.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                redirectURL.append("?" + queryString);
            }
            _logger.info("redirecting logout request: url: {}", redirectURL.toString());
            final HttpServletResponse response = (HttpServletResponse) servletResponse;
            response.sendRedirect(redirectURL.toString());
        }
    }
    
    /**
     * parses the input string to find matches for /logout, /logout.xml, /logout.json (case insensitive)
     * @param input
     * @return true if found, false if not found.
     */
    private boolean findLogoutPattern(String input) {
        return _pattern.matcher(input).find();
    }

    @Override
    protected AbstractRequestWrapper authenticate(ServletRequest servletRequest)
            throws InternalException {
        throw SecurityException.fatals.unsupportedOperation();
    }
}
