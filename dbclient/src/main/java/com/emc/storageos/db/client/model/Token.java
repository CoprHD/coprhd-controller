/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */


package com.emc.storageos.db.client.model;

import java.io.Serializable;
import java.net.URI;

/**
 * 
 *   Authentication token obtained after successfully authenticating
 */
@NoInactiveIndex
@Cf("Token")
public class Token extends BaseToken implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // all timestamps are in minutes
    private Long _lastAccessTime;
    private Long _expirationTime;
    private String _extensions;
    private Long _cacheExpirationTime;
    
    /**
     * This user id is the user id directly associated with the token.
     * Corresponds to the user that will become the active user in the
     * security context when the token is validated.
     */
    private URI _userId;

    /**
     * Field used for indexing token creation time
     */
    private Boolean _indexed;

    /**
     * Return value of indexed field
     * @return
     */
    @Name("indexed")
    @DecommissionedIndex("TokenIndex")
    public Boolean getIndexed() {
        return _indexed;
    }

    public void setIndexed(Boolean indexed) {
        _indexed = indexed;
        setChanged("indexed");
    }

    /**
     * Returns the value of the field called '_userId'.
     * @return Returns the _userId.
     */
    @Name("userid")
    @RelationIndex(cf = "RelationIndex", type = StorageOSUserDAO.class)
    public URI getUserId() {
        return _userId;
    }

    /**
     * Sets the field called '_userId' to the given value.
     * @param userId The _userId to set.
     */
    public void setUserId(URI userId) {
        _userId = userId;
        setChanged("userid");
    }

    @Name("lastAccessTime")
    public Long getLastAccessTime() {
        return _lastAccessTime;
    }

    public void setLastAccessTime(Long accessTime) {
        _lastAccessTime = accessTime;
        setChanged("lastAccessTime");
    }

    @Name("expirationTime")
    public Long getExpirationTime() {
        return _expirationTime;
    }

    public void setExpirationTime(Long expirationTime) {
        _expirationTime = expirationTime;
        setChanged("expirationTime");
    }

    @Name("extensions")
    public String getExtensions() {
        return _extensions;
    }

    /**
     * Sets the field called '_extensions' to the given value.
     * @param extensions The _extensions to set.
     */
    public void setExtensions(String extensions) {
        _extensions = extensions;
        setChanged("extensions");
    }
    
    @Name("cacheExpirationTime")
    public Long getCacheExpirationTime() {
        return _cacheExpirationTime;
    }

    public void setCacheExpirationTime(Long expirationTime) {
        _cacheExpirationTime = expirationTime;
        setChanged("cacheExpirationTime");
    }

}
