/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.validator;

/**
 *  Principal representation for validation
 */
public class StorageOSPrincipal {
    public enum Type {
        User,
        Group
    }
    
    private String _name;
    private Type _type;

    public StorageOSPrincipal(String name, Type type) {
        _name = name;
        _type = type;
    }
    
    public StorageOSPrincipal() {
        _type = Type.User;
    }
    
    public String getName() {
        return _name;
    }

    public void setName(String name) {
        this._name = name;
    }

    public Type getType() {
        return _type;
    }

    public void setType(Type type) {
        this._type = type;
    }
    
    @Override
    public String toString() {
        return _type.toString() + ":" + _name;
    }
}
