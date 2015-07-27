/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.model;

import java.io.Serializable;

/**
 * Wrapper around a CIFS ACE class. This provides higher level information
 * about an access control information than JCIFS would directly provide.
 * 
 * @author Chris Dail
 */
public class ACEWrapper implements Serializable {
    private static final long serialVersionUID = -3090639593197577369L;
    
    private String user;
    private String userId;
    private String access;
    private boolean allow;
    
    public ACEWrapper() { }
    
    public ACEWrapper(String user, String access, boolean allow) {
        this.user = user;
        this.access = access;
        this.allow = allow;
    }

    public String getUser() {
        return user;
    }
    
    public void setUser(String user) {
        this.user = user;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getAccess() {
        return access;
    }
    
    public void setAccess(String access) {
        this.access = access;
    }
    
    public boolean isAllow() {
        return allow;
    }
    
    public void setAllow(boolean allow) {
        this.allow = allow;
    }
    
    public void setLabel(String label) { }
    public String getLabel() {
        StringBuilder sb = new StringBuilder();
        if (allow) {
            sb.append("Allow '");
        }
        else {
            sb.append("Deny '");
        }
        sb.append(user == null ? userId : user);
        sb.append("' ");
        sb.append(access);
        return sb.toString();
    }
    
    @Override public String toString() {
        return getLabel();
    }
}
