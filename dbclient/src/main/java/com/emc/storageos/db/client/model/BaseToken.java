/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.io.Serializable;

/**
 * Base class for an authentication tokens
 */
public abstract class BaseToken extends DataObject implements Serializable {
    private static final long serialVersionUID = 1L;

    private String _zoneId; // == VDCid
    private Long _issuedTime;

    /**
     * convenience function for callers to easily determine if this
     * is a proxy token or not ( since these are the only two types of
     * tokens so far)
     * 
     * @return true if proxy token, false otherwise
     */
    public static boolean isProxyToken(BaseToken t) {
        return ProxyToken.class.isInstance(t);
    }

    /**
     * Returns the value of the field called '_zoneId'.
     * 
     * @return Returns the _zoneId.
     */
    @Name("zoneid")
    public String getZoneId() {
        return _zoneId;
    }

    /**
     * Sets the field called '_zoneId' to the given value.
     * 
     * @param zoneId The _zoneId to set.
     */
    public void setZoneId(String zoneId) {
        _zoneId = zoneId;
        setChanged("zoneid");
    }

    @Name("issuedTime")
    public Long getIssuedTime() {
        return _issuedTime;
    }

    public void setIssuedTime(Long issuedTime) {
        _issuedTime = issuedTime;
        setChanged("issuedTime");
    }
}
