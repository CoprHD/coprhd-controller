/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.model;

import java.io.Serializable;

import org.apache.commons.lang.StringUtils;

public class DiskSummary implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private String diskNumber;
    private String status;
    private String size;
    private String free;
    private String dyn;
    private String gpt;
    
    public String getDiskNumber() {
        return diskNumber;
    }

    public void setDiskNumber(String diskNumber) {
        this.diskNumber = diskNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getFree() {
        return free;
    }

    public void setFree(String free) {
        this.free = free;
    }
    
    public String getDyn() {
        return dyn;
    }

    public void setDyn(String dyn) {
        this.dyn = dyn;
    }
    
    public String getGpt() {
        return gpt;
    }

    public void setGpt(String gpt) {
        this.gpt = gpt;
    }
    
    public String toString() {
        String paddedDiskNumber = StringUtils.rightPad(diskNumber, 10);
        String paddedStatus = StringUtils.rightPad(status, 10);
        String paddedSize = StringUtils.rightPad(size, 8);
        String paddedFree = StringUtils.rightPad(free, 8);
        String paddedDyn = StringUtils.rightPad(dyn, 4);
        String paddedGpt = StringUtils.rightPad(gpt, 4);
        return String.format("%s:%s:%s:%s:%s:%s", paddedDiskNumber, paddedStatus, paddedSize, paddedFree, paddedDyn, paddedGpt);
    }
    
    public String dump() {
        StringBuilder sb = new StringBuilder("\nList Disk:\n");
        sb.append("\tDisk Number:\t").append(diskNumber);
        sb.append("\tStatus:\t").append(status);
        sb.append("\tSize:\t").append(size);
        sb.append("\tFree:\t").append(free);
        sb.append("\tDyn:\t").append(dyn);
        sb.append("\tGPT:\t").append(gpt);
        return sb.toString();
    }
}
