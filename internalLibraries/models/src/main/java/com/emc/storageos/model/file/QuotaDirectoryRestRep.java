/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.file;

import javax.xml.bind.annotation.*;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "quota_directory")
public class QuotaDirectoryRestRep extends DataObjectRestRep {
    private RelatedResourceRep project;
    private String quotaSize;
    private Integer softLimit;
    private Integer softGrace;
    private Integer notificationLimit;
    private RelatedResourceRep parentFileSystem;
    private String nativeId;
    private Boolean oplock;
    private String securityStyle;

    /**
     * native id of quota dir.
     * 
     * @valid none
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
     * @valid true
     * @valid false
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
     * @valid none
     */
    @XmlElement(name = "quota_size_gb")
    public String getQuotaSize() {
        return quotaSize;
    }

    public void setQuotaSize(String size) {
        this.quotaSize = size;
    }
    
    @XmlElement(name="soft_limit", required=false)
    public Integer getSoftLimit() {
        return softLimit;
    }

    public void setSoftLimit(Integer softLimit) {
        this.softLimit = softLimit;
    }

    @XmlElement(name="soft_grace", required=false)
    public Integer getSoftGrace() {
        return softGrace;
    }

    public void setSoftGrace(Integer softGrace) {
        this.softGrace = softGrace;
    }
    @XmlElement(name="notification_limit", required=false)
    public Integer getNotificationLimit() {
        return notificationLimit;
    }

    public void setNotificationLimit(Integer notificationLimit) {
        this.notificationLimit = notificationLimit;
    }

    /**
     * Total capacity of the file system in GB
     * 
     * @valid none
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
     * @valid none
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
     * @valid none
     */
    @XmlElement
    public RelatedResourceRep getParentFileSystem() {
        return parentFileSystem;
    }

    public void setParentFileSystem(RelatedResourceRep fs) {
        this.parentFileSystem = fs;
    }

}
