/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class LdLayout {
    private Integer RAIDType;
    private ArrayList<DiskPatterns> diskPatterns;
    
    public Integer getRAIDType() {
        return RAIDType;
    }
    public void setRAIDType(Integer RAIDType) {
        this.RAIDType = RAIDType;
    }
    public ArrayList<DiskPatterns> getDiskPatterns() {
        return diskPatterns;
    }
    public void setDiskPatterns(ArrayList<DiskPatterns> diskPatterns) {
        this.diskPatterns = diskPatterns;
    }
}
