/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class HostMember {
    private Integer id;
    private String name;
    private ArrayList<FcPath> FCPaths;
    private ArrayList<ISCSIPath> iSCSIPaths;
    
    public Integer getId() {
        return id;
    }
    public void setId(Integer id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public ArrayList<FcPath> getFCPaths() {
        return FCPaths;
    }
    public void setFCPaths(ArrayList<FcPath> fCPaths) {
        FCPaths = fCPaths;
    }
    public ArrayList<ISCSIPath> getiSCSIPaths() {
        return iSCSIPaths;
    }
    public void setiSCSIPaths(ArrayList<ISCSIPath> iSCSIPaths) {
        this.iSCSIPaths = iSCSIPaths;
    }
}
