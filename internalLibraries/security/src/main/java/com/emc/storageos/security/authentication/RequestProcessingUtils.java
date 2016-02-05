/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.authentication;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.keystone.KeystoneConstants;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Utilities class for request processing common constants and coding
 * patterns, related to bourne tokens.
 */
public class RequestProcessingUtils {
    private static final Logger _log = LoggerFactory.getLogger(RequestProcessingUtils.class);
    public static final String REDIRECT_FROM_AUTHSVC = "auth-redirected";
    public static final String KEYSTONE_AUTH_TOKEN_HEADER =  KeystoneConstants.AUTH_TOKEN;
    public static final String AUTH_TOKEN_HEADER = "X-SDS-AUTH-TOKEN";
    public static final String AUTH_PORTAL_TOKEN_HEADER = "X-SDS-PORTAL-AUTH-TOKEN";
    public static final String AUTH_PROXY_TOKEN_HEADER = "X-SDS-AUTH-PROXY-TOKEN";
    public static final String REQUESTING_COOKIES = "using-cookies";
    public static final String REQUESTING_FORMLOGIN = "using-formlogin";
    public static final String UTF8_ENCODING = "UTF-8";
    public static final String FORWARDED_HOST_HEADER = "X-Forwarded-Host";

    /**
     * get Token from the cookie, if there is one
     * 
     * @param request
     * @return
     */
    public static String getTokenFromCookie(final HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookie.getName().equalsIgnoreCase(AUTH_TOKEN_HEADER)) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * get local authsvc redirect
     * 
     * @param req
     * @return
     */
    public static StringBuilder getOnNodeAuthsvcRedirectURL(final HttpServletRequest req, URI endpoint) {
        if (endpoint == null) {
            return null;
        }
        URI myEndPoint = URI.create(req.getRequestURL().toString());
        return new StringBuilder(String.format("https://%s:%s",
                myEndPoint.getHost(), endpoint.getPort()));
    }

    /**
     * if matching key exists in the query string, removes it
     * 
     * @param queryString
     * @param matching
     * @return
     */
    public static String removeFromQueryString(String queryString, String matching)
            throws UnsupportedEncodingException {
        if (queryString == null) {
            return null;
        }
        StringBuffer resultQStr = new StringBuffer();
        for (String pair : queryString.split("&")) {
            int eq = pair.indexOf("=");
            String key = null;
            if (eq < 0) {
                // key with no value
                key = URLDecoder.decode(pair, UTF8_ENCODING);
            } else {
                // key=value
                key = URLDecoder.decode(pair.substring(0, eq), UTF8_ENCODING);
            }
            if (!key.equalsIgnoreCase(matching)) {
                if (resultQStr.length() > 0) {
                    resultQStr.append("&");
                }
                resultQStr.append(pair);
            }
        }
        return resultQStr.toString();
    }

    /**
     * returns true if the provided query parameter is requested
     * 
     * @param queryParam The query parameter to check on
     * @param req
     * @return true if the provided query parameter is set
     */
    public static boolean isRequestingQueryParam(final HttpServletRequest req, String queryParam) {
        if (req.getQueryString() != null && req.getQueryString().contains(queryParam)) {
            try {
                for (String pair : req.getQueryString().split("&")) {
                    int eq = pair.indexOf("=");
                    String key;
                    String value = null;
                    if (eq < 0) {
                        // key with no value
                        key = URLDecoder.decode(pair, UTF8_ENCODING);
                    } else {
                        // key=value
                        key = URLDecoder.decode(pair.substring(0, eq), UTF8_ENCODING);
                        value = URLDecoder.decode(pair.substring(eq + 1), UTF8_ENCODING);
                    }
                    if (key.equalsIgnoreCase(queryParam) &&
                            (value == null || value.equalsIgnoreCase("true"))) {
                        return true;
                    }
                }
            } catch (UnsupportedEncodingException ex) {
                _log.error("exception parsing query string", ex);
                throw APIException.badRequests.parameterIsNotValidURI(
                        URI.create(req.getQueryString()), ex);
            }
        }
        return false;
    }

    /**
     * Method that examines the HTTPServletRequest header searching for
     * the X-Forwarded-Host key. If it's found, this method will return
     * true.
     * 
     * @param req the HttpServletRequest that provides a means to lookup request
     *            header information.
     * @return returns true if the X-Forwarded-Host is set. Otherwise
     *         returns false.
     */
    public static boolean isRequestFromLoadBalancer(final HttpServletRequest req) {
        boolean result = false;
        if (req != null) {
            String lbFlag = req.getHeader(FORWARDED_HOST_HEADER);
            if (lbFlag != null) {
                result = true;
            }
        }
        return result;
    }
}
