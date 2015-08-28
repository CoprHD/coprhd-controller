/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins.common.domainmodel;

import com.emc.storageos.plugins.common.ArgsCreator;

public class Argument {
    private String _method;
    private String _name;
    private Object _value;
    private String _type;

    private ArgsCreator _creator;

    public void setMethod(String _method) {
        this._method = _method;
    }

    public String getMethod() {
        return _method;
    }

    public void setName(String _name) {
        this._name = _name;
    }

    public String getName() {
        return _name;
    }

    public void setValue(Object _value) {
        this._value = _value;
    }

    public Object getValue() {
        return _value;
    }

    public void setCreator(ArgsCreator _creator) {
        this._creator = _creator;
    }

    public ArgsCreator getCreator() {
        return _creator;
    }

    public void setType(String _type) {
        this._type = _type;
    }

    public String getType() {
        return _type;
    }

    @Override
    public String toString() {
        return "method: " + _method + " name: " + _name + " type: " + _type + " value: " + _value;
    }
}
