/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 *  software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of 
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */


package com.emc.storageos.security.geo;


import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.db.client.model.Token;
import com.emc.storageos.geomodel.TokenResponse;
import com.emc.storageos.security.authentication.TokenKeyGenerator.TokenKeysBundle;
import com.emc.storageos.security.SerializerUtils;

/**
 * Utility class to build a TokenResponse using Token, TokenKeyBundle and StorageOSUserDAO
 * objects as inputs
 */
public class TokenResponseBuilder {
    private static final Logger log = LoggerFactory.getLogger(TokenResponseBuilder.class);
    
    /**
     * Creates a TokenResponse from a token and user dao object
     * @param token
     * @param user
     * @return
     */
    public static TokenResponse buildTokenResponse(Token token, StorageOSUserDAO user, TokenKeysBundle tokenBundle) {
        TokenResponse response = new TokenResponse();
        try {
            if (user != null) {
                response.setUserDAO(SerializerUtils.serializeAsBase64EncodedString(user));
            }
            if (token != null) {
                response.setToken(SerializerUtils.serializeAsBase64EncodedString(token));
            }
            if (tokenBundle != null) {
                response.setTokenKeysBundle(SerializerUtils.serializeAsBase64EncodedString(tokenBundle));
            }
        } catch (IOException e) {
            log.error("Could not serialize/encode TokenReponse artifacts", e);
            return null;
        } 
        return response;
    }
    
    /**
     * Creates a TokenResponseArtifacts holder for items retrieved in a TokenResponse.
     * Today, Token and StorageOSUserDAO objects
     * @param response
     * @return
     */
    public static TokenResponseArtifacts parseTokenResponse(TokenResponse response) {
        String userEncoded = response.getUserDAO();
        String tokenEncoded = response.getToken();
        String tokenKeysBundleEncoded = response.getTokenKeysBundle();
        
        StorageOSUserDAO user = null;
        Token token = null;
        TokenKeysBundle tokenKeysBundle = null;

        if (StringUtils.isNotBlank(userEncoded)) {   
            try {
                user = (StorageOSUserDAO) SerializerUtils.deserialize(userEncoded);
            }
            catch (UnsupportedEncodingException e) {
                log.error("Could not decode user: ", e);
            }
            catch (Exception e) {
                log.error("Could not deserialize user: ", e);
            }
        }
        if (StringUtils.isNotBlank(tokenEncoded)) {   
            try {
                token = (Token) SerializerUtils.deserialize(tokenEncoded);
            }
            catch (UnsupportedEncodingException e) {
                log.error("Could not decode token: ", e);
            }
            catch (Exception e) {
                log.error("Could not deserialize token: ", e);
            }
        }
        if (StringUtils.isNotBlank(tokenKeysBundleEncoded)) {   
            try {
                tokenKeysBundle = (TokenKeysBundle) SerializerUtils.deserialize(tokenKeysBundleEncoded);
            }
            catch (UnsupportedEncodingException e) {
                log.error("Could not decode token keys bundle: ", e);
            }
            catch (Exception e) {
                log.error("Could not deserialize token keys bundle: ", e);
            }
        }
    
        return new TokenResponseBuilder.TokenResponseArtifacts(user, token, tokenKeysBundle);  
    }
    
    /**
     * Wrapper class to hold user record, token, and token keys   
     *      
    */
    public static class TokenResponseArtifacts {
        private StorageOSUserDAO user;
        private Token token;
        private TokenKeysBundle bundle;
        
        public TokenResponseArtifacts(StorageOSUserDAO u, Token t, TokenKeysBundle b) {
            user = u;
            token = t;
            bundle = b;
        }
        
        public StorageOSUserDAO getUser() {
            return user;
        }
        
        public Token getToken() {
            return token;
        }
        
        public TokenKeysBundle getTokenKeysBundle() {
            return bundle;
        }
    }
}
