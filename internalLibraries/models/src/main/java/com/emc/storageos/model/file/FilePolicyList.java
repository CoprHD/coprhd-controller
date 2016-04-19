/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * This Class represents a return type that returns the id, name and self link
 * for a list of schedule policies.
 * 
 * @author prasaa9
 * 
 */
@XmlRootElement(name = "file_schedule_policies")
public class FilePolicyList {

    // List of schedule policies
    private List<FilePolicyRestRep> filePolicies;

    @XmlElement(name = "file_policy")
    public List<FilePolicyRestRep> getFilePolicies() {
        return filePolicies;
    }

    public void setFilePolicies(List<FilePolicyRestRep> filePolicies) {
        this.filePolicies = filePolicies;
    }

}
