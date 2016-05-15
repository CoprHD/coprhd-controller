/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class PortStatisticsCommandResult {
    private Integer total;
    private ArrayList<PortStatMembers> members;

    public Integer getTotal() {
        return total;
    }
    public void setTotal(Integer total) {
        this.total = total;
    }
    public ArrayList<PortStatMembers> getMembers() {
        return members;
    }
    public void setMembers(ArrayList<PortStatMembers> members) {
        this.members = members;
    }
}
