/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.net.URI;

@ExcludeFromGarbageCollection
@Cf("UserPasswordGroup")
public class UserPasswordGroup extends DataObject {
    private String _encodedPassword;
    private StringSet _groups;
    private URI _tenant;
    private String _namespace;
    private String _userMetadata;

    public UserPasswordGroup() {
        super();
    }

    @Name("encodedPassword")
    public String getEncodedPassword() {
        return _encodedPassword;
    }

    public void setEncodedPassword(String encodedPassword) {
        _encodedPassword = encodedPassword;
        setChanged("encodedPassword");
    }

    @Name("userGroups")
    public StringSet getGroups() {
        return _groups;
    }

    public void setGroups(StringSet groups) {
        _groups = groups;
        setChanged("userGroups");
    }

    @Name("tenant")
    @RelationIndex(cf = "RelationIndex", type = TenantOrg.class)
    public URI getTenant() {
        return _tenant;
    }

    public void setTenant(URI tenant) {
        _tenant = tenant;
        setChanged("tenant");
    }

    @Name("namespace")
    public String getNamespace() {
        return _namespace;
    }

    public void setNamespace(String namespace) {
        _namespace = namespace;
        setChanged("namespace");
    }

    @Name("userMetadata")
    public String getUserMetadata() {
        return _userMetadata;
    }

    public void setUserMetadata(String userMetadata) {
        _userMetadata = userMetadata;
        setChanged("userMetadata");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("encodedPassword: ");
        sb.append(_encodedPassword);
        sb.append(", userGroups: ");
        sb.append(_groups);
        sb.append(", tenant: ");
        sb.append(_tenant);
        sb.append(", namespace: ");
        sb.append(_namespace);
        sb.append(", userMetadata: ");
        sb.append(_userMetadata);
        return sb.toString();
    }
}