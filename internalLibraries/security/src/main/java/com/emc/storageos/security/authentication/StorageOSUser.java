/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.authentication;

import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.db.client.model.StringSet;
import org.springframework.util.CollectionUtils;

import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
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
        return (_roles == null) ? new HashSet<String>() : Collections.unmodifiableSet(_roles);
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
        sb.append(setToString(_roles));
        sb.append("; groups: ");
        sb.append(setToString(_groups));
        sb.append("; attributes: ");
        sb.append(setToString(_attributes));
        return sb.toString();
    }

    /**
     * return the string representation of a string set.
     * 
     * @param stringSet
     * @return
     */
    private String setToString(Set stringSet) {
        if (CollectionUtils.isEmpty(stringSet)) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (Object item : stringSet) {
            sb.append((String) item + ",");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * clone StorageOSUser
     * 
     * @return
     */
    public StorageOSUser clone() {
        StorageOSUser clone = new StorageOSUser(this._userName, this._tenantId);
        clone._local = this._local;
        clone._isProxied = this._isProxied;
        clone._id = this._id;
        clone._proxyToken = this._proxyToken;
        clone._token = this._token;

        // attributes
        StringSet attributes = new StringSet();
        Iterator<String> itAttr = _attributes.iterator();
        while (itAttr.hasNext()) {
            attributes.add(itAttr.next());
        }
        clone._attributes = attributes;

        // groups
        StringSet groups = new StringSet();
        Iterator<String> itGroup = _groups.iterator();
        while (itGroup.hasNext()) {
            groups.add(itGroup.next());
        }
        clone._groups = groups;
        return clone;
    }
}
