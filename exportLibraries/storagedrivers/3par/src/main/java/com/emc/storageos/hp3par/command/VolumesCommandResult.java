/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class VolumesCommandResult {
    private Integer total;
    private ArrayList<VolumeDetailsCommandResult> members;
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
    public ArrayList<VolumeDetailsCommandResult> getMembers() {
        return members;
    }
    public void setMembers(ArrayList<VolumeDetailsCommandResult> members) {
        this.members = members;
    }
}
