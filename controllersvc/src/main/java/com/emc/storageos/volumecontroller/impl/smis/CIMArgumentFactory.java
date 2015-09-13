/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis;

import javax.cim.*;

/**
 * Factory for creating CIMArgument objects
 */
public class CIMArgumentFactory {

    public CIMArgument<UnsignedInteger16> uint16(String name, int value) {
        return build(name, new UnsignedInteger16(value));
    }

    public CIMArgument<UnsignedInteger16[]> uint16Array(String name, UnsignedInteger16[] value) {
        return build(name, value);
    }

    public CIMArgument<UnsignedInteger32[]> uint32Array(String name, UnsignedInteger32[] value) {
        return build(name, value);
    }

    public CIMArgument<UnsignedInteger32> uint32(String name, int value) {
        return build(name, new UnsignedInteger32(value));
    }

    public CIMArgument<UnsignedInteger64> uint64(String name, String value) {
        return build(name, new UnsignedInteger64(value));
    }

    public CIMArgument<UnsignedInteger64> uint64(String name, Long value) {
        return uint64(name, value.toString());
    }

    public CIMArgument<UnsignedInteger64[]> uint64Array(String name, UnsignedInteger64[] value) {
        return build(name, value);
    }

    public CIMArgument<String> string(String name, String value) {
        return build(name, value);
    }

    public CIMArgument<String[]> stringArray(String name, String[] value) {
        return build(name, value);
    }

    public CIMArgument<CIMObjectPath> reference(String name, CIMObjectPath path) {
        return build(name, path);
    }

    public CIMArgument<CIMObjectPath[]> referenceArray(String name, CIMObjectPath[] path) {
        return build(name, path);
    }

    public CIMArgument<Boolean> bool(String name, Boolean value) {
        return build(name, value);
    }

    public CIMArgument<Boolean[]> boolArray(String name, Boolean[] value) {
        return build(name, value);
    }

    public CIMArgument<Object> object(String name, Object value) {
        return build(name, value);
    }

    private <T> CIMArgument<T> build(String name, T value) {
        CIMArgument<T> arg;
        try {
            arg = new CIMArgument<>(name, CIMDataType.getDataType(value), value);
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ", e);
        }
        return arg;
    }
}
