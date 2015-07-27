/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * Placeholder data object for secret key API.
 */
@ExcludeFromGarbageCollection
@DbKeyspace(DbKeyspace.Keyspaces.GLOBAL)
@Cf("UserSecretKey")
public class UserSecretKey extends DataObject {
    private String _firstKey;
    private String _firstKeyTime;
    private Long _firstKeyExpiryTime;
    private String _secondKey;
    private String _secondKeyTime;
    private Long _secondKeyExpiryTime;
    private URI    _tenant;
    private String _namespace;

    // Since _dbClient does not guarantee support for null values in the String,
    // empty string is used to indicate inactive keys.
    public UserSecretKey() {
        super();
        _firstKey = "";
        _firstKeyTime = "";
        _secondKey = "";
        _secondKeyTime = "";
    }

    @Encrypt
    @Name("firstKey")
    public String getFirstKey() {
        return _firstKey;
    }

    public void setFirstKey(String firstKey) {
        _firstKey = firstKey;
        setChanged("firstKey");
    }

    @Name("firstKeyTime")
     public String getFirstKeyTime() {
        return _firstKeyTime;
    }

    public void setFirstKeyTime(String firstKeyTime) {
        _firstKeyTime = firstKeyTime;
        setChanged("firstKeyTime");
    }

    @Name("firstKeyExpiryTime")
    public Long getFirstKeyExpiryTime() {
        return _firstKeyExpiryTime;
    }

    public void setFirstKeyExpiryTime(Long firstKeyExpiryTimeTime) {
        _firstKeyExpiryTime = firstKeyExpiryTimeTime;
        setChanged("firstKeyExpiryTime");
    }

    @Encrypt
    @Name("secondKey")
    public String getSecondKey() {
        return _secondKey;
    }

    public void setSecondKey(String secondKey) {
        _secondKey = secondKey;
        setChanged("secondKey");
    }

    @Name("secondKeyTime")
    public String getSecondKeyTime() {
        return _secondKeyTime;
    }

    public void setSecondKeyTime(String secondKeyTime) {
        _secondKeyTime = secondKeyTime;
        setChanged("secondKeyTime");
    }

    @Name("secondKeyExpiryTime")
    public Long getSecondKeyExpiryTime() {
        return _secondKeyExpiryTime;
    }

    public void setSecondKeyExpiryTime(Long secondKeyExpiryTime) {
        _secondKeyExpiryTime = secondKeyExpiryTime;
        setChanged("secondKeyExpiryTime");
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("tenant: ");
        sb.append(_tenant.toString());
        sb.append(", namespace: ");
        sb.append(_namespace);
        sb.append(", id: ");
        sb.append(getId());
        sb.append(", firstKey: ");
        sb.append(_firstKey);
        sb.append(", firstKeyTime: ");
        sb.append(_firstKeyTime);
        sb.append(", secondKey: ");
        sb.append(_secondKey);
        sb.append(", secondKeyTime: ");
        sb.append(_secondKeyTime);
        return sb.toString();
    }
 
}
