/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.model.file;

import javax.xml.bind.annotation.*;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.LinkedHashSet;
import java.util.Set;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "quota_directory")
public class QuotaDirectoryRestRep extends DataObjectRestRep {
    private RelatedResourceRep project;
    private String quotaSize;
    private RelatedResourceRep parentFileSystem;
    private String nativeId;
    private Boolean oplock;
    private String securityStyle;
    
    /**
     * native id of quota dir.
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
     * @valid none 
     */
    @XmlElement(name = "quota_size_gb")
    public String getQuotaSize() {
        return quotaSize;
    }

    public void setQuotaSize(String size) {
        this.quotaSize = size;
    }

    /**
     * Total capacity of the file system in GB
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
