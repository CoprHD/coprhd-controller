/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class CPGCommandResult {
    private Integer total;
    private ArrayList<CPGMember> members;
    
    public Integer getTotal() {
        return total;
    }
    public void setTotal(Integer total) {
        this.total = total;
    }
    public ArrayList<CPGMember> getMembers() {
        return members;
    }
    public void setMembers(ArrayList<CPGMember> members) {
        this.members = members;
    }   
}
