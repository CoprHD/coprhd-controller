/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class UserRoleCommandResult {
    private String username;
    private ArrayList<Privileges> privileges;
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public ArrayList<Privileges> getPrivileges() {
        return privileges;
    }
    public void setPrivileges(ArrayList<Privileges> privileges) {
        this.privileges = privileges;
    }
}
