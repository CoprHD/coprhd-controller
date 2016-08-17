/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Attributes associated with a file system quota directory,
 * specified during its creation.
 * 
 */
@XmlRootElement(name = "quota_directory_create")
public class QuotaDirectoryCreateParam {

    private String name;
    private Boolean oplock;
    private String size; // Quota size - hard limit.
    private int softLimit;
    private int notificationLimit;
    private int softGrace;

    // UNIX, NTFS, Mixed
    private String securityStyle = "unix";

    public QuotaDirectoryCreateParam() {
    }

    public QuotaDirectoryCreateParam(String name) {
        this.name = name;
    }

    public QuotaDirectoryCreateParam(String name, String securityStyle) {
        this.name = name;
        this.securityStyle = securityStyle;
    }

    public QuotaDirectoryCreateParam(String name, boolean oplock, String size, String securityStyle) {
        this.name = name;
        this.oplock = oplock;
        this.size = size;
        this.securityStyle = securityStyle;
    }

    /**
     * User provided name of the quota directory.
     * 
     */
    @XmlElement(name = "name", required = true)
    public String getQuotaDirName() {
        return name;
    }

    public void setQuotaDirName(String name) {
        this.name = name;
    }

    /**
     * Limit total space usage within this quota directory (in Bytes)
     * 
     */
    @XmlElement(name = "size")
    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    @XmlElement(name="soft_limit")
    public int getSoftLimit() {
        return softLimit;
    }

    public void setSoftLimit(int softLimit) {
        this.softLimit = softLimit;
    }

    @XmlElement(name="notification_limit")
    public int getNotificationLimit() {
        return notificationLimit;
    }

    public void setNotificationLimit(int notificationLimit) {
        this.notificationLimit = notificationLimit;
    }

    @XmlElement(name="soft_grace")
    public int getSoftGrace() {
        return softGrace;
    }

    public void setSoftGrace(int softGrace) {
        this.softGrace = softGrace;
    }

    /**
     * Flag to specify Read/Write cache enable for this quota directory.
     * 
     */
    @XmlElement(name = "oplock")
    public Boolean getOpLock() {
        return oplock;
    }

    public void setOpLock(Boolean oplock) {
        this.oplock = oplock;
    }

    /**
     * Security style for the quota directory.
     * Default is "unix".
     * Valid values:
     *   parent
     *   unix
     *   ntfs
     *   mixed
     */
    @XmlElement(name = "security_style")
    public String getSecurityStyle() {
        return securityStyle;
    }

    public void setSecurityStyle(String securityStyle) {
        this.securityStyle = securityStyle;
    }

}
