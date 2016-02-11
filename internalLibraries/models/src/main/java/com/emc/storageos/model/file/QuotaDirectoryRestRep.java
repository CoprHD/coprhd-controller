/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.file;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "quota_directory")
public class QuotaDirectoryRestRep extends DataObjectRestRep {
    private RelatedResourceRep project;
    private String quotaSize;
    private Integer softLimit;
    private Integer softGrace;
    private Boolean softLimitExceeded;
    private Integer notificationLimit;
    private RelatedResourceRep parentFileSystem;
    private String nativeId;
    private Boolean oplock;
    private String securityStyle;

    /**
     * native id of quota dir.
     * 
     */
    @XmlElement(name = "native_id")
    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    /**
     * Specifies whether or not oplocks enabled or not.
     * 
     * @return true if oplocks enabled.
     */
    @XmlElement(name = "oplock")
    public Boolean getOpLock() {
        return oplock;
    }

    public void setOpLock(Boolean oplock) {
        this.oplock = oplock;
    }

    /**
     * Total capacity of the file system in GB
     * 
     */
    @XmlElement(name = "quota_size_gb")
    public String getQuotaSize() {
        return quotaSize;
    }

    public void setQuotaSize(String size) {
        this.quotaSize = size;
    }

    @XmlElement(name = "soft_limit", required = false)
    public Integer getSoftLimit() {
        return softLimit;
    }

    public void setSoftLimit(Integer softLimit) {
        this.softLimit = softLimit;
    }

    @XmlElement(name = "soft_grace", required = false)
    public Integer getSoftGrace() {
        return softGrace;
    }

    public void setSoftGrace(Integer softGrace) {
        this.softGrace = softGrace;
    }

    @XmlElement(name = "soft_limit_exceeded", required = false)
    public Boolean getSoftLimitExceeded() {
        return softLimitExceeded;
    }

    public void setSoftLimitExceeded(Boolean softLimitExceeded) {
        this.softLimitExceeded = softLimitExceeded;
    }

    @XmlElement(name = "notification_limit", required = false)
    public Integer getNotificationLimit() {
        return notificationLimit;
    }

    public void setNotificationLimit(Integer notificationLimit) {
        this.notificationLimit = notificationLimit;
    }

    /**
     * Total capacity of the file system in GB
     * 
     */
    @XmlElement(name = "security_style")
    public String getSecurityStyle() {
        return securityStyle;
    }

    public void setSecurityStyle(String securityStyle) {
        this.securityStyle = securityStyle;
    }

    /**
     * URI for the project containing the parent file system.
     * 
     */
    @XmlElement
    public RelatedResourceRep getProject() {
        return project;
    }

    public void setProject(RelatedResourceRep project) {
        this.project = project;
    }

    /**
     * URI for the project containing the parent file system.
     * 
     */
    @XmlElement
    public RelatedResourceRep getParentFileSystem() {
        return parentFileSystem;
    }

    public void setParentFileSystem(RelatedResourceRep fs) {
        this.parentFileSystem = fs;
    }

}
