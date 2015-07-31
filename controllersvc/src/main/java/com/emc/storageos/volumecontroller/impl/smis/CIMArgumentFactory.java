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
        CIMArgument<UnsignedInteger16> argument;
        try {
            argument = new CIMArgument<UnsignedInteger16>(name, CIMDataType.UINT16_T,
                    new UnsignedInteger16(value));
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return argument;
    }

    public CIMArgument<UnsignedInteger16[]> uint16Array(String name, UnsignedInteger16[] value) {
        CIMArgument<UnsignedInteger16[]> argument;
        try {
            argument = new CIMArgument<UnsignedInteger16[]>(name, CIMDataType.UINT16_ARRAY_T, value);
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return argument;
    }

    public CIMArgument<UnsignedInteger32[]> uint32Array(String name, UnsignedInteger32[] value) {
        CIMArgument<UnsignedInteger32[]> argument;
        try {
            argument = new CIMArgument<UnsignedInteger32[]>(name, CIMDataType.UINT32_ARRAY_T, value);
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return argument;
    }

    public CIMArgument<UnsignedInteger32> uint32(String name, int value) {
        CIMArgument<UnsignedInteger32> argument;
        try {
            argument = new CIMArgument<UnsignedInteger32>(name, CIMDataType.UINT32_T,
                    new UnsignedInteger32(value));
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return argument;
    }

    public CIMArgument<UnsignedInteger64> uint64(String name, String value) {
        CIMArgument<UnsignedInteger64> argument;
        try {
            argument = new CIMArgument<UnsignedInteger64>(name, CIMDataType.UINT64_T,
                    new UnsignedInteger64(value));
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return argument;
    }

    public CIMArgument<UnsignedInteger64> uint64(String name, Long value) {
        CIMArgument<UnsignedInteger64> argument;
        try {
            argument = new CIMArgument<UnsignedInteger64>(name, CIMDataType.UINT64_T,
                    new UnsignedInteger64(value.toString()));
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return argument;
    }

    public CIMArgument<String> string(String name, String value) {
        CIMArgument<String> argument;
        try {
            argument = new CIMArgument<String>(name, CIMDataType.STRING_T, value);
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return argument;
    }

    public CIMArgument<String[]> stringArray(String name, String[] value) {
        CIMArgument<String[]> argument;
        try {
            argument = new CIMArgument<String[]>(name, CIMDataType.STRING_ARRAY_T, value);
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return argument;
    }

    public CIMArgument<CIMObjectPath> reference(String name, CIMObjectPath path) {
        CIMArgument<CIMObjectPath> argument;
        try {
            argument = new CIMArgument<CIMObjectPath>(name,
                    CIMDataType.getDataType(path), path);
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ", e);
        }
        return argument;
    }

    public CIMArgument<CIMObjectPath[]> referenceArray(String name, CIMObjectPath[] path) {
        CIMArgument<CIMObjectPath[]> argument;
        try {
            argument = new CIMArgument<CIMObjectPath[]>(name,
                    CIMDataType.getDataType(path), path);
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return argument;
    }

    public CIMArgument<Boolean> bool(String name, Boolean value) {
        CIMArgument<Boolean> bool;
        try {
            bool = new CIMArgument<Boolean>(name, CIMDataType.BOOLEAN_T, value);
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return bool;
    }

    public CIMArgument<Object> object(String name, Object value) {
        CIMArgument<Object> obj;
        try {
            obj = new CIMArgument<Object>(name, CIMDataType.OBJECT_T, value);
        } catch (Exception e) {
            throw new IllegalStateException("Problem getting input arguments: ");
        }
        return obj;
    }
}
