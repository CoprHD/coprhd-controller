/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.model.wmi;

public enum RegistryValueType {
    STRING(1), EXPANDED_STRING(2), BINARY(3), DWORD(4), MULTI_STRING(7);

    private int value;

    RegistryValueType(int value) {
        this.value = value;
    }

    public static int toValue(RegistryValueType item) {
        return item.value;
    }

    public static RegistryValueType fromValue(int value) {
        for (RegistryValueType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("No such registry value type: " + value);
    }
}
