/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.net.URI;

public class OpenstackToken extends DataObject {
    // encrypted token
    private String _token;
    // server's end point (i.e., storageUrl)
    private String _serviceEndPoint;
    // all the groups the user belongs to
    private StringSet _groups;
    // if user is tenant admin
    private Boolean _isTenantAdmin = false;
    // token expire time
    private String _tokenExpire;
    // tenant id user belongs to
    private URI _tenantId;
    // namespace the tenant mapping to
    private String _namespace;

    @Name("token")
    @AlternateId("AltIdIndex")
    public String getToken() {
        return _token;
    }

    public void setToken(String token) {
        if (token == null) {
            token = "";
        }
        _token = token;
        setChanged("token");
    }

    @Name("serviceEndPoint")
    public String getServiceEndPoint() {
        return _serviceEndPoint;
    }

    public void setServiceEndPoint(String serviceEndPoint) {
        if (serviceEndPoint == null) {
            serviceEndPoint = "";
        }
        _serviceEndPoint = serviceEndPoint;
        setChanged("serviceEndPoint");
    }

    @Name("userGroups")
    public StringSet getGroups() {
        return _groups;
    }

    public void setGroups(StringSet groups) {
        _groups = groups;
        setChanged("userGroups");
    }

    @Name("isTenantAdmin")
    public Boolean getIsTenantAdmin() {
        return _isTenantAdmin;
    }

    public void setIsTenantAdmin(Boolean isTenantAdmin) {
        _isTenantAdmin = isTenantAdmin;
        setChanged("isTenantAdmin");
    }

    @Name("tokenExpire")
    public String getTokenExpire() {
        return _tokenExpire;
    }

    public void setTokenExpire(String expire) {
        if (expire == null) {
            expire = "";
        }
        _tokenExpire = expire;
        setChanged("tokenExpire");
    }

    @Name("tenantId")
    public URI getTenantId() {
        return _tenantId;
    }

    public void setTenantId(URI tenantId) {
        _tenantId = tenantId;
        setChanged("tenantId");
    }

    @Name("namespace")
    public String getNamespace() {
        return _namespace;
    }

    public void setNamespace(String namespace) {
        if (namespace == null) {
            namespace = "";
        }
        _namespace = namespace;
        setChanged("namespace");
    }
}
