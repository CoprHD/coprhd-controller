/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.authentication;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.commons.lang.StringUtils;

/**
 * Client to make internal api logout calls to local authsvc
 */
public class InternalLogoutClient {
    private static final Logger log = LoggerFactory.getLogger(InternalLogoutClient.class);
    private static final int MAX_LOGOUT_RETRIES = 5;
    private static final URI LOGOUT_URI = URI.create("/logout");

    @Autowired
    private AuthSvcEndPointLocator authSvcEndPointLocator;

    @Autowired
    protected TokenEncoder tokenEncoder;
    

    /**
     * Basic internal api call to authsvc to logout a user.
     * @param username optional.  If passed, that user will be logged out (if the token
     * present in the request corresponds to a user with SECURITY_ADMIN role).  Else,
     * the user corresponding to the token from the request is what will get logged out.
     * @param req
     * @return
     */
    public boolean logoutUser(String username, HttpServletRequest req) {
        return logoutUser(username, req, true, true);
    }
    
    /**
     * Alternate version of the internal api call to authsvc which allows toggling
     * the force flag on/off, and allows multiple retries or not.
     * @param username
     * @param req
     * @param force
     * @param retry
     * @return
     */
    public boolean logoutUser(String username, HttpServletRequest req, boolean force, 
            boolean retry) {
        // get the auth token from the request, we need to pass
        // it along the logout request
        String rawToken = req.getHeader(RequestProcessingUtils.AUTH_TOKEN_HEADER);
        if (rawToken == null) {
            if (req.getCookies() != null) {
                for (Cookie cookie: req.getCookies()) {
                    if (cookie.getName().equalsIgnoreCase(RequestProcessingUtils.AUTH_TOKEN_HEADER)) {
                        rawToken = cookie.getValue();
                        log.debug("Got token from cookies for internal logout request");
                        break;
                    }
                }
            }
        }
       
        TokenOnWire tw = tokenEncoder.decode(rawToken);
        if (tw == null) {
            log.error("Could not logout user.  Token does not decode.");
            return false;
        }

        
        /**
         * - If no username parameter is supplied, this is a regular logout or logout force
         * for a given token.  In this case do notify other VDCs if you are the originator of this token.
         * Other wise, do not notify (you are being notified).
         * - If the username parameter is supplied, this is a logout?username=<user>, notify is false because
         * either this is invoked from PasswordService and this is always for local users, or this invoked
         * from TokenService.logout which received this logout?username= request from another vdc, so no
         * need to propagate further (this would cause an infinite loop)
         */
        boolean notify = false;
        if (StringUtils.isBlank(username)) {
            log.debug("no username");
            notify = VdcUtil.getLocalShortVdcId().equals(URIUtil.parseVdcIdFromURI(tw.getTokenId())) ? true : false;
        } 
        
        // perform logout of the local copy, if notifyVDC is true, this will also notify vdcs that have a copy
        // of the token.
        log.info("LogoutClient: {}", notify ? "will set the notify flag to true when sending logout request to authsvc" : 
            "Just deleting local copy of token.");
        
        String endpoint = null;
        int attempts = 0;

        if (StringUtils.isNotBlank(username)) {
            try {
                username = URLEncoder.encode(username, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw APIException.badRequests.unableToEncodeString(username, e);
            }
        }

        int retries = retry ?  MAX_LOGOUT_RETRIES : 1;
        while(attempts < retries) {
            log.debug("Logout attempt {}", ++attempts);
            AuthSvcClientIterator authSvcClientItr = new AuthSvcClientIterator(authSvcEndPointLocator);
            try {           
                if(authSvcClientItr.hasNext()) {
                    endpoint = authSvcClientItr.peek().toString();            
                    log.debug("AuthenticationProvider endpoint: {}", endpoint);

                    String fullRequest = LOGOUT_URI + String.format("?force=%s&proxytokens=false&notifyvdcs=%s%s", 
                            force == true ? "true" : "false", 
                                    notify == true ? "true" : "false", 
                                            username == null ? "" : "&username=" + username);
                    log.info(fullRequest);
                    final ClientResponse response = authSvcClientItr.get(URI.create(fullRequest), rawToken);
                    final int status = response.getStatus();        
                    String errorRaw = response.getEntity(String.class);
                    log.debug("Status: {}", status);
                    log.debug("Response entity: {}", errorRaw);

                    if( status == ClientResponse.Status.OK.getStatusCode() ) {
                        log.info("User logged out successfully.  User will have to re-login.");
                        return true;
                    } else if (status == ClientResponse.Status.UNAUTHORIZED.getStatusCode() &&
                            !notify) {
                        log.info("401 Status code from logout request.  Token did not exist or was already deleted.");
                    } else {
                        log.warn("Unexpected response code {}.", status);
                    }
                }
            } catch (Exception e) {
                log.info("Exception connecting to {}. ", endpoint, e);
            }
        }        
        return false;
    }
}
