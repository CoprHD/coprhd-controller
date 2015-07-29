/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 *  software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of 
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.security.authentication;

import java.net.URI;

import com.emc.storageos.db.client.model.BaseToken;
import com.emc.storageos.db.client.model.SerializationIndex;

/**
 * Represents the token on the wire. This is the class that will be
 * serialized, encoded and signed.
 */
public class TokenOnWire {
    private String _VDCid;
    private String _encryptionKeyId;
    private URI _tokenId;
    boolean _proxyToken = false;

    /**
     * Creates a TokenOnWire instance from a BaseToken data model object
     * 
     * @param token t (can be proxytoken or regular token)
     * @return
     */
    public static TokenOnWire createTokenOnWire(BaseToken t) {
        TokenOnWire tw = new TokenOnWire(t.getId(), BaseToken.isProxyToken(t));
        return tw;
    }

    /**
     * This constructor is only to be used for testing. Any code path that
     * use actual encoding need to use the factory method above.
     * 
     * @param id
     */
    public TokenOnWire(URI id) {
        _tokenId = id;
    }

    /**
     * Constructor for deserialization only.
     */
    public TokenOnWire() {
    }

    /**
     * Private constructor to create a TokenOnWire
     * 
     * @param id
     * @param zoneId
     * @param isProxyToken
     */
    private TokenOnWire(URI id, boolean isProxyToken) {
        _tokenId = id;
        _proxyToken = isProxyToken;
    }

    /**
     * Returns true if this TokenOnWire was created from a proxytoken.
     * False otherwise.
     * 
     * @return true | false.
     */
    @SerializationIndex(2)
    public boolean isProxyToken() {
        return _proxyToken;
    }

    /**
     * sets the _proxyToken property to true or false
     * 
     * @param is
     */
    public void setProxyToken(boolean is) {
        _proxyToken = is;
    }

    /**
     * Returns the value of the field called '_zoneId'.
     * 
     * @return Returns the _zoneId.
     */
    @Deprecated
    @SerializationIndex(3)
    public String getVDCId() {
        return _VDCid;
    }

    /**
     * sets the zone id
     * 
     * @param id
     */
    public void setVDCId(String id) {
        _VDCid = id;
    }

    /**
     * Returns the value of the field called '_encryptionKeyId'.
     * 
     * @return Returns the _encryptionKeyId.
     */
    @SerializationIndex(4)
    public String getEncryptionKeyId() {
        return _encryptionKeyId;
    }

    /**
     * Sets the field called '_encryptionKeyId' to the given value.
     * 
     * @param _encryptionKeyId The _encryptionKeyId to set.
     */
    public void setEncryptionKeyId(String encryptionKeyId) {
        _encryptionKeyId = encryptionKeyId;
    }

    /**
     * Returns the value of the field called '_tokenId'.
     * 
     * @return Returns the _tokenId.
     */
    @SerializationIndex(5)
    public URI getTokenId() {
        return _tokenId;
    }

    /**
     * sets the token id
     * 
     * @param id
     */
    public void setTokenId(URI id) {
        _tokenId = id;
    }

}