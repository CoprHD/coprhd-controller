/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.password;

import java.io.Serializable;

public class InvalidLogins implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    private Long _lastAccessTime; // last time the login from this IP was not successful in minutes
    private Long _errorLoginAttempts;  // Number of login attemps from this IP
    
    /**
     * @param lastAccessTime the current time when this record is created
     * @param loginattempts the initial value for the login attempts
     */
    public InvalidLogins(long lastAccessTime, long loginattempts) {
        _lastAccessTime = lastAccessTime;
        _errorLoginAttempts = loginattempts;
    }
    
    /**
     * Increments the login attempts count
     */
    public void incrementErrorLoginCount() {
        _errorLoginAttempts++;
    }
    
    /**
     * @return the current number of invalid login attempts
     */
    public long getLoginAttempts() {
        return _errorLoginAttempts;
    }
    
    /**
     * @param lastAccessTime set the last invalid access time
     */
    public void setLastAccessTime(long lastAccessTime) {
        if (lastAccessTime > 0) {
            _lastAccessTime = lastAccessTime;
        } else {
            // Illegal value for last access time
            throw new IllegalArgumentException("The argument to setAccessTime is less than or equal to 0");
        }
    }
    
    /**
     * @return the last invalid access time
     */
    public long getLastAccessTime() {
        return _lastAccessTime;
    }

}
