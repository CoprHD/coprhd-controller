/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.audit;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import com.emc.storageos.security.authorization.QueriedObjectCache;

import com.emc.storageos.security.authentication.RequestProcessingUtils;

import javax.ws.rs.core.HttpHeaders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Generic use http request logging filter.
 */

public class RequestAuditFilter implements Filter {
    private static final Logger _log = LoggerFactory.getLogger(RequestAuditFilter.class);

    @Autowired
    RequestStatTracker _requestTracker;

    @Override
    public void destroy() {
        // nothing to do.
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {

        beforeRequest();

        HttpServletRequest req = (HttpServletRequest) request;

        // Request logging
        String srcHost = req.getHeader("X-Real-IP");
        if (srcHost == null) {
            srcHost = req.getRemoteAddr();
        }

        String rqs = req.getQueryString();
        String basicStr = String.format("Request: %s - %s - %s from %s", req.getMethod(),
                req.getRequestURL(), (rqs == null ? "empty-query" : rqs), srcHost);
        _log.info(stripCookieToken(basicStr));

        String detailsStr = String.format("Auth headers: Auth Token: %s - Proxy Token: %s - Basic Auth: %s - Keystone Token:%s",
                req.getHeader(RequestProcessingUtils.AUTH_TOKEN_HEADER) == null ? "no" : "yes",
                req.getHeader(RequestProcessingUtils.AUTH_PROXY_TOKEN_HEADER) == null ? "no" : "yes",
                req.getHeader(HttpHeaders.AUTHORIZATION) == null ? "no" : "yes",
                req.getHeader(RequestProcessingUtils.KEYSTONE_AUTH_TOKEN_HEADER) == null ? "no" : "yes");
        _log.info(detailsStr);

        // Additional fine grained debugging
        if (_log.isDebugEnabled()) {
            String authT = req.getHeader(RequestProcessingUtils.AUTH_TOKEN_HEADER);
            if (authT != null && authT.equals("")) {
                _log.debug("Auth token header provided but value was empty.  This will most likely cause a 401.");
            }

            String authPT = req.getHeader(RequestProcessingUtils.AUTH_PROXY_TOKEN_HEADER);
            if (authPT != null && authPT.equals("")) {
                _log.debug("Proxy token header provided but value was empty.  This will most likely cause a 401.");
            }
            String authKT = req.getHeader(RequestProcessingUtils.KEYSTONE_AUTH_TOKEN_HEADER);
            if (authKT != null && authKT.equals("")) {
                _log.debug("Keystone token header provided but value was empty.  This will most likely cause a 401.");
            }
        }

        // let's look at cookies
        Cookie[] cookies = req.getCookies();
        if (cookies != null && cookies.length > 0) {
            for (Cookie c : cookies) {
                if (c.getName().equalsIgnoreCase(RequestProcessingUtils.AUTH_TOKEN_HEADER)) {
                    if (c.getValue().equals("")) {
                        _log.debug("Auth token cookie was provided but value was empty.  This will most likely cause a 401.");
                    } else {
                        _log.info("Auth token provided in cookie");
                        String cookieStr = String.format("Cookie: %s : %s", c.getName(), c.getValue());
                        _log.debug(cookieStr);
                    }
                } else {
                    String cookieStr = (_log.isDebugEnabled()) ? String.format("Cookie: %s : %s", c.getName(), c.getValue()) : String
                            .format("Cookie: %s", c.getName());
                    _log.info(cookieStr);
                }
            }
        } else {
            _log.debug("No cookies");
        }

        // Follow the rest of the chain
        HttpServletResponseWrapperWithStatus responseS = new HttpServletResponseWrapperWithStatus((HttpServletResponse) response);
        try {
            filterChain.doFilter(request, responseS);
        } catch (RuntimeException ex) {
            afterRequest();
            throw ex;
        }
        if (responseS.getStatus() >= 500) {
            _requestTracker.flag500Error();
        }

        afterRequest();

        // Response logging
        HttpServletResponse resp = (HttpServletResponse) response;
        String respHeadersStr = String.format("Response headers: %s", resp.toString());
        if (_log.isDebugEnabled()) {
            _log.debug(respHeadersStr); // Show the auth cookie in context of the request
        } else {
            _log.info(stripCookieToken(respHeadersStr));
        }
    }

    // Suppressing: Removeing this hard-coded password since it is just a key name
    @SuppressWarnings({ "squid:S2068" })
    public static String stripCookieToken(String str) {
        Pattern p = Pattern.compile("(?s).*(X-SDS-\\S*:|password=)\\s*([^\\r]*)\\s*\\r?\\n?");
        Matcher m = p.matcher(str);

        if (m.find()) {
            String ss = m.group(2);
            return str.replaceAll(ss, " ** masked ** ");
        } else {
            return str;
        }
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
        // nothing to do
    }

    /**
     * Servlet response wrapper class to extract the response status code
     * before sending it. More recent versions of the servlet api have a getStatus
     * built in the servletresponse and don't require this.
     */
    private class HttpServletResponseWrapperWithStatus extends HttpServletResponseWrapper {

        private int _httpStatus;

        public HttpServletResponseWrapperWithStatus(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void sendError(int sc) throws IOException {
            _httpStatus = sc;
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            _httpStatus = sc;
            super.sendError(sc, msg);
        }

        @Override
        public void setStatus(int sc) {
            _httpStatus = sc;
            super.setStatus(sc);
        }

        public int getStatus() {
            return _httpStatus;
        }

    }

    private void beforeRequest() {
        // clear the cache before we do anything else since we sit at the top
        // of the filter chain.
        QueriedObjectCache.clearCache();
        _requestTracker.incrementActiveRequests();
        _requestTracker.recordStartTime();
    }

    private void afterRequest() {
        _requestTracker.decrementActiveRequests();
        _requestTracker.recordEndTime();
    }

}
