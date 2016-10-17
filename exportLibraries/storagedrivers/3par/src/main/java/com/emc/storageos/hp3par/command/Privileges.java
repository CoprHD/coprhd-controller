/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

public class Privileges {
    String domain;
    String role;
    
    public String getDomain() {
        return domain;
    }
    public void setDomain(String domain) {
        this.domain = domain;
    }
    public String getRole() {
        return role;
    }
    public void setRole(String role) {
        this.role = role;
    }
}
