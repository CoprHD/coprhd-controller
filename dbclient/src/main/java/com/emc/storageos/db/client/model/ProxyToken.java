/**
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


package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 *   Proxy token obtained from Token
 */
@NoInactiveIndex
@Cf("ProxyToken")
public class ProxyToken extends BaseToken {
    // user name to facilitate proxytoken lookups
    protected String _userName;
    // set of the last known user ids.  Will come into play
    // later when updating user records.
    protected StringSet _lastKnownUserIds = new StringSet();
    // the time the user was last validated. We need to revalidate after a set amount of
    // time has passed.
    private Long _lastValidatedTime;

    /**
     * Returns the value of the field called '_userName'.
     * @return Returns the _userName.
     */
    @Name("username")
    @AlternateId("AltIdIndex")
    public String getUserName() {
        return _userName;
    }

    /**
     * Sets the field called '_userName' to the given value.
     * @param userName The _userName to set.
     */
    public void setUserName(String userName) {
        _userName = userName;
        setChanged("username");
    }

    /**
     * Returns the value of the field called '_lastKnownIds'.
     * @return Returns the _lastKnownIds.
     */
    @Name("lastKnownIds")
    @RelationIndex(cf = "RelationIndex", type = StorageOSUserDAO.class)
    @IndexByKey
    public StringSet getLastKnownIds() {
        return _lastKnownUserIds;
    }

    /**
     * Sets the field called '_lastKnownIds' to the given value.
     * @param ids The _lastKnownIds to set.
     */
    public void setLastKnownIds(StringSet ids) {
        _lastKnownUserIds = ids;
        setChanged("lastKnownIds");
    }

    /**
     * convenience function to one of the known ids as a URI
     * @return
     */
    public URI peekLastKnownId() {
        if(_lastKnownUserIds.isEmpty()) {
            return null;
        } else {
            return URI.create(_lastKnownUserIds.iterator().next());
        }
    }


    /**
     * add a user id to the set of last known user ids
     * @param id
     */
    public void addKnownId(URI id) {
        if (_lastKnownUserIds == null) {
            _lastKnownUserIds = new StringSet();
        }
        _lastKnownUserIds.add(id.toString());
    }

    /**
     * @return the _lastValidatedTime
     */
    @Name("lastValidatedTime")
    public Long getLastValidatedTime() {
        return _lastValidatedTime;
    }

    /**
     * @param _lastValidatedTime
     *            the _lastValidatedTime to set
     */
    public void setLastValidatedTime(Long _lastValidatedTime) {
        this._lastValidatedTime = _lastValidatedTime;
        setChanged("lastValidatedTime");
    }

}
