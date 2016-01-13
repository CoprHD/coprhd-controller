/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Attributes associated with a file system Quota directory, specified
 * during the Quota directory creation.
 * 
 */

@XmlRootElement(name = "quota_directory_modify")
public class QuotaDirectoryUpdateParam {
    private Boolean oplock;
    private String size; // Quota size - hard limit.
    // UNIX, NTFS, Mixed
    private String securityStyle;
    private String softLimit;
    private String notificationLimit;
    private String softGracePeriod;

    public QuotaDirectoryUpdateParam() {
    }

    public QuotaDirectoryUpdateParam(Boolean oplock, String size, String securityStyle) {
        this.oplock = oplock;
        this.size = size;
        this.securityStyle = securityStyle;
    }

    /**
     * Limit total space usage within this file system directory in Bytes.
     * 
     * @valid none
     */
    @XmlElement(name = "size")
    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    /**
     * Flag to specify Read/Write cache enable for this quota directory.
     * 
     * @valid true
     * @valid false
     */
    @XmlElement(name = "oplock")
    public Boolean getOpLock() {
        return oplock;
    }

    public void setOpLock(Boolean oplock) {
        this.oplock = oplock;
    }

    /**
     * Security style for the Quota directory. Default is
     * "UNIX".
     * 
     * @valid "UNIX" = Security style by default
     * @valid "NTFS"
     * @valid "Mixed"
     */
    @XmlElement(name = "security_style")
    public String getSecurityStyle() {
        return securityStyle;
    }

    public void setSecurityStyle(String securityStyle) {
        this.securityStyle = securityStyle;
    }
    
    @XmlElement(name="softLimit")
    public String getSoftLimit() {
        return softLimit;
    }

    public void setSoftLimit(String softLimit) {
        this.softLimit = softLimit;
    }

    @XmlElement(name="notificationLimit")
    public String getNotificationLimit() {
        return notificationLimit;
    }

    public void setNotificationLimit(String notificationLimit) {
        this.notificationLimit = notificationLimit;
    }

    @XmlElement(name="softGracePeriod")
    public String getSoftGracePeriod() {
        return softGracePeriod;
    }

    public void setSoftGracePeriod(String softGracePeriod) {
        this.softGracePeriod = softGracePeriod;
    }
}
