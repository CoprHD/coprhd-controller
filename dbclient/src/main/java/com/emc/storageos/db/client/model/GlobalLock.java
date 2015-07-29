/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation All Rights Reserved This software contains the
 * intellectual property of EMC Corporation or is licensed to EMC Corporation from third parties.
 * Use of this software and the intellectual property contained therein is expressly limited to the
 * terms and conditions of the License Agreement under which it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.model.DbKeyspace.Keyspaces;

/**
 * Global Lock
 */
@Cf("GlobalLock")
@DbKeyspace(Keyspaces.GLOBAL)
public class GlobalLock {
    public static final String GL_MODE_COLUMN = "mode";
    public static final String GL_OWNER_COLUMN = "owner";
    public static final String GL_EXPIRATION_COLUMN = "expirationTime";

    public enum GL_Mode {
        GL_NodeSvcShared_MODE("GL_NodeSvcShared_MODE"),
        GL_VdcShared_MODE("GL_VdcShared_MODE"),
        GL_Exclusive_MODE("GL_Exclusive_MODE");

        private final String name;

        private GL_Mode(String s) {
            name = s;
        }

        public boolean equalsName(String otherName) {
            return (otherName == null) ? false : name.equals(otherName);
        }

        public String toString() {
            return name;
        }
    }

    // CF Key
    private String _name;

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    // CF Columns
    private String _owner;
    private String _mode;
    private String _expirationTime;

    public String getOwner() {
        return _owner;
    }

    public void setOwner(String owner) {
        _owner = owner;
    }

    public String getMode() {
        return _mode;
    }

    public void setMode(String mode) {
        _mode = mode;
    }

    public String getExpirationTime() {
        return _expirationTime;
    }

    public void setExpirationTime(String expirationTime) {
        _expirationTime = expirationTime;
    }
}
