/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.authentication;

import com.emc.storageos.db.client.model.StorageOSUserDAO;

import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * StorageOS user profile information.
 */
public class StorageOSUser extends StorageOSUserDAO implements Principal {
    private Set<String> _roles = null;
    private String _token = null;
    private String _proxyToken = null;

    private boolean _isProxied = false;

    public StorageOSUser(String username, String tenantId) {
        _userName = username;
        _tenantId = tenantId;
        
        _isProxied = false;
    }

    public StorageOSUser(StorageOSUserDAO dao) {
        _userName = dao.getUserName();
        _tenantId = dao.getTenantId();
        _attributes = dao.getAttributes();
        _groups = dao.getGroups();
        _local = dao.getIsLocal();
        _id = dao.getId();
        _isProxied = false;
    }

    @Override
    public String getName() {
        return _userName;
    }
    
    public void setToken(final String token) {
        _token = token;
    }

    public String getToken() {
        return _token;
    }
    
    public void setProxyToken(final String ptoken) {
        _proxyToken = ptoken;
    }

    public String getProxyToken() {
        return _proxyToken;
    }

    public Set<String> getRoles() {
        return (_roles == null)? new HashSet<String>(): Collections.unmodifiableSet(_roles);
    }
    
    public void setRoles(Set<String> roles) {
        _roles = roles;
    }

    public void addRole(String role) {
        if (_roles == null) {
            _roles = new HashSet<String>();
        }
        _roles.add(role);
    }

    public boolean isLocal() {
        return _local;
    }
    
    public boolean isProxied() {
        return _isProxied;
    }
    
    public void setIsProxied(boolean proxied) {
        _isProxied = proxied;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(" user: ");
        sb.append(_userName);
        if (isProxied()) {
            sb.append(" proxied ");
        }
        sb.append("; tenantId: ");
        sb.append(_tenantId);
        sb.append("; roles: ");
        sb.append(_roles!=null?_roles.toArray():"None");
        sb.append("; attributes: ");
        sb.append(_attributes!=null?_attributes.toArray():"None");
        return sb.toString();
    }
}
