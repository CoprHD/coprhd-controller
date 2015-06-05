/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.windows.model.wmi;

public class RegistryValueDef {
    private String name;
    private RegistryValueType type;

    public RegistryValueDef() {
    }

    public RegistryValueDef(String name, RegistryValueType type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RegistryValueType getType() {
        return type;
    }

    public void setType(RegistryValueType type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return name + " [" + type + "]";
    }
}
