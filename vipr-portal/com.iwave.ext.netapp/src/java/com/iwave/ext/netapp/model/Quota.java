/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netapp.model;

import java.io.Serializable;
import java.util.List;

import com.google.common.collect.Lists;

public class Quota implements Serializable {

    private String diskLimit;
    private String diskUsed;
    private String fileLimit;
    private String filesUsed;
    private String quotaTarget;
    private String quotaType;
    private List<QuotaUser> quotaUsers = Lists.newArrayList();
    private String softDiskLimit;
    private String softFileLimit;
    private String threshold;
    private String qtree;
    private String vfiler;
    private String volume;

    public String getDiskLimit() {
        return diskLimit;
    }

    public void setDiskLimit(String diskLimit) {
        this.diskLimit = diskLimit;
    }

    public String getDiskUsed() {
        return diskUsed;
    }

    public void setDiskUsed(String diskUsed) {
        this.diskUsed = diskUsed;
    }

    public String getFileLimit() {
        return fileLimit;
    }

    public void setFileLimit(String fileLimit) {
        this.fileLimit = fileLimit;
    }

    public String getFilesUsed() {
        return filesUsed;
    }

    public void setFilesUsed(String filesUsed) {
        this.filesUsed = filesUsed;
    }

    public String getQuotaTarget() {
        return quotaTarget;
    }

    public void setQuotaTarget(String quotaTarget) {
        this.quotaTarget = quotaTarget;
    }

    public String getQuotaType() {
        return quotaType;
    }

    public void setQuotaType(String quotaType) {
        this.quotaType = quotaType;
    }

    public List<QuotaUser> getQuotaUsers() {
        return quotaUsers;
    }

    public void setQuotaUsers(List<QuotaUser> quotaUsers) {
        this.quotaUsers = quotaUsers;
    }

    public String getSoftDiskLimit() {
        return softDiskLimit;
    }

    public void setSoftDiskLimit(String softDiskLimit) {
        this.softDiskLimit = softDiskLimit;
    }

    public String getSoftFileLimit() {
        return softFileLimit;
    }

    public void setSoftFileLimit(String softFileLimit) {
        this.softFileLimit = softFileLimit;
    }

    public String getThreshold() {
        return threshold;
    }

    public void setThreshold(String threshold) {
        this.threshold = threshold;
    }

    public String getQtree() {
        return qtree;
    }

    public void setQtree(String qtree) {
        this.qtree = qtree;
    }

    public String getVfiler() {
        return vfiler;
    }

    public void setVfiler(String vfiler) {
        this.vfiler = vfiler;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

}
