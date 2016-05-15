/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class CPGCommandResult {
    private Integer total;
    private ArrayList<CPGMembers> members;
    
    public Integer getTotal() {
        return total;
    }
    public void setTotal(Integer total) {
        this.total = total;
    }
    public ArrayList<CPGMembers> getMembers() {
        return members;
    }
    public void setMembers(ArrayList<CPGMembers> members) {
        this.members = members;
    }   
}
