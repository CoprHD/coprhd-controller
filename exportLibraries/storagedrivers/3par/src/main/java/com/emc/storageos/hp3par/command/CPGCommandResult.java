package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class CPGCommandResult {
    private Integer total;
    private ArrayList<Members> members;
    
    public Integer getTotal() {
        return total;
    }
    public void setTotal(Integer total) {
        this.total = total;
    }
    public ArrayList<Members> getMembers() {
        return members;
    }
    public void setMembers(ArrayList<Members> members) {
        this.members = members;
    }   
}
