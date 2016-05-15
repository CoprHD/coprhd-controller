/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class PortCommandResult {
    private Integer total;
    private ArrayList<PortMembers> members;
    
    public Integer getTotal() {
        return total;
    }
    public void setTotal(Integer total) {
        this.total = total;
    }
    public ArrayList<PortMembers> getMembers() {
        return members;
    }
    public void setMembers(ArrayList<PortMembers> members) {
        this.members = members;
    }
}
