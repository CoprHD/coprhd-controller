/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.auth;

import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

@XmlAccessorType(XmlAccessType.PROPERTY)
public class InvalidLogins implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private Long _lastAccessTime; // last time the login from this IP was not successful in minutes
    private Long _errorLoginAttempts;  // Number of login attemps from this IP
    private String _clientIP;


    public InvalidLogins() {
        // emtpy constructor
    }

    /**
     * InvalidLogins constructor
     *
     * @param clientIP
     * @param lastAccessTime the current time when this record is created
     * @param loginattempts the initial value for the login attempts
     */


    public InvalidLogins(String clientIP, long lastAccessTime, long loginattempts) {
        _clientIP = clientIP;
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
    @XmlElement(name = "login_attempts")
    @JsonProperty("login_attempts")
    public long getLoginAttempts() {
        return _errorLoginAttempts;
    }

    /**
     * @param loginAttempts
     */
    public void setLoginAttempts(long loginAttempts) {
        _errorLoginAttempts = loginAttempts;
    }
    
    /**
     * @param lastAccessTime set the last invalid access time
     */
    public void setLastAccessTimeInLong(long lastAccessTime) {
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
    @XmlElement(name = "last_access_time")
    @JsonProperty("last_access_time")
    public String getLastAccessTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(_lastAccessTime * 60 * 1000));
    }

    public void setLastAccessTime(String lastAccessTime) throws Exception {
        _lastAccessTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(lastAccessTime).getTime();
    }


    @XmlTransient
    public long getLastAccessTimeInLong() {
        return _lastAccessTime;
    }


    public void setClientIP(String clientIP) {
        _clientIP = clientIP;
    }


    @XmlElement(name = "client_ip")
    @JsonProperty("client_ip")
    public String getClientIP() {
        return _clientIP;
    }

}
