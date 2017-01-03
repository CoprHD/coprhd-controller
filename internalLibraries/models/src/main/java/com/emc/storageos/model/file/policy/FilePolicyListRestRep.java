/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "file_policies")
public class FilePolicyListRestRep {

    private List<NamedRelatedResourceRep> filePolicies;

    @XmlElement(name = "file_policy")
    public List<NamedRelatedResourceRep> getFilePolicies() {

        if (filePolicies == null) {
            filePolicies = new ArrayList<NamedRelatedResourceRep>();
        }

        return filePolicies;
    }

    public void setFilePolicies(List<NamedRelatedResourceRep> filePolicies) {
        this.filePolicies = filePolicies;
    }

    public void add(NamedRelatedResourceRep filePolicy) {
        if (filePolicies == null) {
            filePolicies = new ArrayList<NamedRelatedResourceRep>();
        }
        filePolicies.add(filePolicy);
    }

}
