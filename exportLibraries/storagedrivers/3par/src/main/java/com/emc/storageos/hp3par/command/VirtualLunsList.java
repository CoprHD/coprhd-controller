/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class VirtualLunsList{
    private Integer total;
    private ArrayList<VirtualLun> members;    
    
	public Integer getTotal() {
        return total;
    }
    public void setTotal(Integer total) {
        this.total = total;
    }
    public ArrayList<VirtualLun> getMembers() {
        return members;
    }
    public void setMembers(ArrayList<VirtualLun> members) {
        this.members = members;
    }
}
