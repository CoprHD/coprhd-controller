/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geo.service.impl.resource;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.db.client.model.Token;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.security.authentication.TokenKeyGenerator.TokenKeysBundle;
import com.emc.storageos.geo.service.authentication.InterVDCHMACAuthFilter;
import com.emc.storageos.geomodel.TokenResponse;
import com.emc.storageos.geomodel.request.TokenKeysRequest;
import com.emc.storageos.security.authentication.CassandraTokenValidator;
import com.emc.storageos.security.authentication.InternalLogoutClient;
import com.emc.storageos.security.authentication.TokenKeyGenerator;
import com.emc.storageos.security.authentication.RequestProcessingUtils;
import com.emc.storageos.security.authentication.TokenValidator;
import com.emc.storageos.security.geo.RequestedTokenHelper;
import com.emc.storageos.security.geo.RequestedTokenHelper.Operation;
import com.emc.storageos.security.geo.TokenResponseBuilder;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Service to perform Token and user lookups to other VDCs, logouts...
 * */
@Path(InterVDCHMACAuthFilter.INTERVDC_URI + "token")
public class TokenService {

    private static Logger log = LoggerFactory.getLogger(TokenService.class);

    @Autowired
    private DbClient dbClient;

    @Autowired
    private TokenValidator tokenValidator;

    @Autowired
    private TokenKeyGenerator tokenKeyGenerator;

    @Autowired
    private RequestedTokenHelper tokenMapHelper;

    @Autowired
    private InternalLogoutClient internalLogoutClient;

    /**
     * Retrieves Token and UserDAO records from a passed in auth token (header)
     * TokenKeysRequest can also contain key ids to look at. If they don't match the local
     * TokenKeysBundle, send the updated bundle in the response
     * 
     * @param httpRequest
     * @return TokenResponse with token and userDAO records populated.
     */
    @POST
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public TokenResponse getToken(@Context HttpServletRequest httpRequest, TokenKeysRequest req) {
        String rawToken = httpRequest.getHeader(RequestProcessingUtils.AUTH_TOKEN_HEADER);
        String firstKey = req.getFirstKeyId();
        String secondKey = req.getSecondKeyId();

        Token token = null;
        StorageOSUserDAO user = null;
        TokenKeysBundle updatedBundle = null;

        // validate token if provided
        if (StringUtils.isNotBlank(rawToken)) {
            token = (Token) tokenValidator.verifyToken(rawToken);
            if (token != null) {
                user = tokenValidator.resolveUser(token);
            }
            if (user == null || token == null) {
                throw APIException.unauthorized.noTokenFoundForUserFromForeignVDC();
            }
            if (user.getIsLocal()) {
                throw APIException.forbidden.localUsersNotAllowedForSingleSignOn(user.getUserName());
            }
        }

        // compare key ids to local tokenkeybundle if provided (prevKey will always be
        // provided if a bundle was sent. CurKey may be null. In other words, there may
        // not has been a rotation yet.
        if (StringUtils.isNotBlank(firstKey)) {
            try {
                updatedBundle = tokenKeyGenerator.readBundle();
            } catch (Exception ex) {
                log.error("Could not look at local token keys bundle");
            }
            if (updatedBundle != null) { // if we found a bundle
                log.debug("Read the local key bundle");
                // look at its key ids
                List<String> keyIds = updatedBundle.getKeyEntries();
                if ((firstKey.equals(keyIds.get(0)) && secondKey == null && keyIds.size() == 1) ||
                        (firstKey.equals(keyIds.get(0)) && secondKey != null && secondKey.equals(keyIds.get(1)))) {
                    log.info("Key id match.  Not returning a bundle");
                    // if they both match what was passed in, make the bundle null and
                    // return that. Caller has updated keys and does not need them.
                    updatedBundle = null;
                } else {
                    log.info("Key ids do not match.  Returning updated bundle");
                }
            }
        }

        if (token != null) {
            tokenMapHelper.addOrRemoveRequestingVDC(Operation.ADD_VDC, token.getId().toString(),
                    req.getRequestingVDC());
            // update idle time on original token. Since it is being borrowed by another vdc,
            // it just got accessed.
            token.setLastAccessTime(CassandraTokenValidator.getCurrentTimeInMins());
            try {
                dbClient.persistObject(token);
            } catch (DatabaseException ex) {
                log.error("failed updating last access time for borrowed token {}", token.getId());
            }
        }
        return TokenResponseBuilder.buildTokenResponse(token, user, updatedBundle);
    }

    /**
     * 
     * Makes an internal api call to authsvc to logout the token present in the request.
     * 
     * @param httpRequest where to find the token
     * @param force is the force(true/false) parameter that will be relayed to authsvc/logout
     * @param username is the username parameter that will be relayed to authsvc/logout
     * @return
     */
    @POST
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    @Path("/logout")
    public Response logoutToken(@Context HttpServletRequest httpRequest,
            @QueryParam("username") String username, @QueryParam("force") boolean force) {
        boolean res = internalLogoutClient.logoutUser(username, httpRequest, force, false);
        return Response.ok().build();
    }
}
