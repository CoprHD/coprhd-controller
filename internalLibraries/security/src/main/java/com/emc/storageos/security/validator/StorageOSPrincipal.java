/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.validator;

/**
 * Principal representation for validation
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
