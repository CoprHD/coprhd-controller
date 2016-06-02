/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class VolumesCommandResult {
    private Integer total;
    private ArrayList<VolumeMember> members;
    private ArrayList<Links> links;
    
    public ArrayList<Links> getLinks() {
		return links;
	}
	public void setLinks(ArrayList<Links> links) {
		this.links = links;
	}
	public Integer getTotal() {
        return total;
    }
    public void setTotal(Integer total) {
        this.total = total;
    }
    public ArrayList<VolumeMember> getMembers() {
        return members;
    }
    public void setMembers(ArrayList<VolumeMember> members) {
        this.members = members;
    }
}
