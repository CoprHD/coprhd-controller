/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.io.Serializable;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class FilePolicyStorageResources implements Serializable {

    private static final long serialVersionUID = -4590555905523347134L;

    private List<FilePolicyStorageResourceRestRep> policyStorageResources;

    @XmlElementWrapper(name = "file_policy_storage_resources")
    @XmlElement(name = "file_policy_storage_resource")
    public List<FilePolicyStorageResourceRestRep> getStorageResources() {
        return policyStorageResources;
    }

    public void setStorageResources(List<FilePolicyStorageResourceRestRep> policyStorageResources) {
        this.policyStorageResources = policyStorageResources;
    }

}
