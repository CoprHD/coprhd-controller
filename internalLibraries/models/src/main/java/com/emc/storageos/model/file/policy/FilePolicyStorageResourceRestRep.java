/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;

/**
 * 
 * @author jainm15
 *
 */

@XmlRootElement
public class FilePolicyStorageResourceRestRep extends DataObjectRestRep {

    private static final long serialVersionUID = 1L;
    private NamedRelatedResourceRep filePolicy;
    private RelatedResourceRep storageSystem;
    private URI nasServer;
    private NamedRelatedResourceRep appliedAt;
    private String policyNativeId;
    private String resourcePath;
    private String nativeGuid;

    @XmlElement(name = "filePolicy")
    public NamedRelatedResourceRep getFilePolicy() {
        return filePolicy;
    }

    public void setFilePolicy(NamedRelatedResourceRep filePolicy) {
        this.filePolicy = filePolicy;
    }

    @XmlElement(name = "storageSystem")
    public RelatedResourceRep getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(RelatedResourceRep storageSystem) {
        this.storageSystem = storageSystem;
    }

    @XmlElement(name = "nasServer")
    public URI getNasServer() {
        return nasServer;
    }

    public void setNasServer(URI nasServer) {
        this.nasServer = nasServer;
    }

    @XmlElement(name = "appliedAt")
    public NamedRelatedResourceRep getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(NamedRelatedResourceRep appliedAt) {
        this.appliedAt = appliedAt;
    }

    @XmlElement(name = "policyNativeId")
    public String getPolicyNativeId() {
        return policyNativeId;
    }

    public void setPolicyNativeId(String policyNativeId) {
        this.policyNativeId = policyNativeId;
    }

    @XmlElement(name = "resourcePath")
    public String getResourcePath() {
        return resourcePath;
    }

    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    @XmlElement(name = "nativeGuid")
    public String getNativeGuid() {
        return nativeGuid;
    }

    public void setNativeGuid(String nativeGuid) {
        this.nativeGuid = nativeGuid;
    }

}
