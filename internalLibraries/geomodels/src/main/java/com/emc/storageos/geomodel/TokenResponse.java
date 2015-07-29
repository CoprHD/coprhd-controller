/*
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

package com.emc.storageos.geomodel;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * This class represents a response to a token request from a remote vdc.
 * It will contain a Token Object, And StorageOSDAO object.
 * Additionally, the reponse may contain a token key bundle object.
 */
@XmlRootElement
public class TokenResponse {
    private String token;
    private String userDAO;
    private String tokenKeysBundle;

    /**
     * Returns the value of the field called 'token'.
     * 
     * @return Returns the token.
     */
    @XmlElement
    public String getToken() {
        return this.token;
    }

    /**
     * Sets the field called 'token' to the given value.
     * 
     * @param token The token to set.
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * Returns the value of the field called 'userDAO'.
     * 
     * @return Returns the userDAO.
     */
    @XmlElement(name = "user_dao")
    public String getUserDAO() {
        return this.userDAO;
    }

    /**
     * Sets the field called 'userDAO' to the given value.
     * 
     * @param userDAO The userDAO to set.
     */
    public void setUserDAO(String userDAO) {
        this.userDAO = userDAO;
    }

    /**
     * Returns the value of the field called 'tokenKeysBundle'.
     * 
     * @return Returns the tokenKeysBundle.
     */
    @XmlElement(name = "token_keys_bundle")
    public String getTokenKeysBundle() {
        return this.tokenKeysBundle;
    }

    /**
     * Sets the field called 'tokenKeysBundle' to the given value.
     * 
     * @param tokenKeysBundle The tokenKeysBundle to set.
     */
    public void setTokenKeysBundle(String tokenKeysBundle) {
        this.tokenKeysBundle = tokenKeysBundle;
    }

}
