/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller;

import java.io.Serializable;
import java.net.URI;

import com.emc.storageos.db.client.model.QuotaDirectory;

/**
 * Place holder for FS QuotaDirectory information.
 */
@SuppressWarnings("serial")
public class FileShareQuotaDirectory implements Serializable {

    // enumeration of qtree security styles
    public enum SecurityStyles {
        parents,
        unix,
        ntfs,
        mixed
    }

    private URI _id;
    private String _name;
    private String _securityStyle;
    private Boolean _oplock;
    private Long _size;
    private int softLimit;
    private int notificationLimit;
    private int softGrace;

    // private SecurityStyles _securityStyle;

    public String getSecurityStyle() {
        return _securityStyle.toString();
    }

    /**
     * Construction of FileShareQtree
     * 
     * @param name
     */
    public FileShareQuotaDirectory(QuotaDirectory qtree) {
        _id = qtree.getId();
        _name = qtree.getName();
        _securityStyle = qtree.getSecurityStyle();
        _oplock = qtree.getOpLock();
        _size = qtree.getSize();
        this.softLimit = qtree.getSoftLimit();
        this.softGrace = qtree.getSoftGrace();
        this.notificationLimit = qtree.getNotificationLimit();
    }

    public String getName() {
        return _name;
    }

    public URI getId() {
        return _id;
    }

    public void setId(URI id) {
        _id = id;
    }

    public Boolean getOpLock() {
        return _oplock;
    }

    public Long getSize() {
        return _size;
    }

    public int getSoftLimit() {
        return softLimit;
    }

    public void setSoftLimit(int softLimit) {
        this.softLimit = softLimit;
    }

    public int getNotificationLimit() {
        return notificationLimit;
    }

    public void setNotificationLimit(int notificationLimit) {
        this.notificationLimit = notificationLimit;
    }

    public int getSoftGrace() {
        return softGrace;
    }

    public void setSoftGrace(int softGrace) {
        this.softGrace = softGrace;
    }

}
