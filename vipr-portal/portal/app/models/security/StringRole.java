/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models.security;

import models.deadbolt.Role;

/**
 * Deadbolt 'Role' that is backed by a string containing the role name.
 *
 * @author Chris Dail
 */
public class StringRole implements Role {
    private String roleName;

    public StringRole(String roleName) {
        this.roleName = roleName;
    }

    @Override
    public String getRoleName() {
        return roleName;
    }

    @Override
    public String toString() {
        return roleName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
        	return true;
        }
        if (o == null || getClass() != o.getClass()) {
        	return false;
        }

        StringRole that = (StringRole) o;

        if (roleName != null ? !roleName.equals(that.roleName) : that.roleName != null) {
        	return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return roleName != null ? roleName.hashCode() : 0;
    }
}
