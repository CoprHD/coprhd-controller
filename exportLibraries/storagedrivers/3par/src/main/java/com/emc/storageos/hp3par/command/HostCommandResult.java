/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class HostCommandResult {
    private Integer total;
    private ArrayList<HostMember> members;
    
    public Integer getTotal() {
        return total;
    }
    public void setTotal(Integer total) {
        this.total = total;
    }
    public ArrayList<HostMember> getMembers() {
        return members;
    }
    public void setMembers(ArrayList<HostMember> members) {
        this.members = members;
    }
}
