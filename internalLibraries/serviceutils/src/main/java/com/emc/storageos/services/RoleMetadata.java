/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.services;

/**
 * Class that defines role properties like its name.
 * A role is a group of services typically deployed together.
 */
public class RoleMetadata {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
