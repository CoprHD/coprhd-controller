/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.authorization;

import java.net.URI;

import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Class for representing the keys we use for saving roles/acls in db
 */
public class PermissionsKey {
    public enum Type {
        SID,
        GROUP,
        TENANT,
    }
    private Type _type;
    private String _value;
    // for acls, we have an extra specifier
    // its tenantorg for project acls, and CoS type for CoS acls
    private String _specifier;

    /**
     * Default constructor
     */
    public PermissionsKey() {   }

    /**
     * Constructor for type and value
     */
    public PermissionsKey(Type prefix, String value) {
        _type = prefix;
        _value = normalizeValue(prefix,value);
        _specifier = null;
    }

    /**
     * Constructor for type, value and tenant id (used for project acls)
     */
    public PermissionsKey(Type prefix, String value, URI id) {
        _type = prefix;
        _value = normalizeValue(prefix,value);
        _specifier = (id != null) ? id.toString() : null;
    }

    /**
     * Constructor for type, value and tenant id (used for CoS and Neighborhood acls)
     */
    public PermissionsKey(Type prefix, String value, String spec) {
        _type = prefix;
        _value = normalizeValue(prefix,value);
        _specifier = spec;
    }

    /**
     * Parses the given string to populate fields of the object
     * @param in String representation of the key
     * @throws IllegalArgumentException
     */
    public void parseFromString(String in) throws IllegalArgumentException {
        String[] parts = in.split(",");
        if (parts.length > 1) {
            _type = Type.valueOf(parts[0]);
            _value = normalizeValue(_type,parts[1]);
        } else {
            throw APIException.badRequests.theParametersAreNotValid(in);
        }
        if (parts.length > 2) {
            // group role key
            _specifier = parts[2];
        }
    }

    /**
     * Get type of the key
     * @return Type
     * @See PermissionsKey.Type
     */
    public Type getType() {
        return _type;
    }

    /**
     * Get actual value of the key
     * this is the subject-id for subject key, group name for group key
     * @return String
     */
    public String getValue() {
        return _value;
    }

    @Override
    public String toString() {
        if (_specifier != null) {
            return String.format("%s,%s,%s", _type.toString(), _value, _specifier);
        } else {
            return String.format("%s,%s", _type.toString(), _value);
        }
    }
    
    /**
     * Convert the provided value to a suitable value for a key
     * @param type Type of the permissions key
     * @param value permissions key value
     * @return normailzed value of the key
     */
    private String normalizeValue( Type type, String value ) {        
        if( type.equals(Type.GROUP) ) {
            return value.toUpperCase();
        } else if( type.equals(Type.SID) ) {            
            return value.toLowerCase();
        }
        return value;
    }
}
