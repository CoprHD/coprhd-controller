/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Length;

@XmlRootElement(name = "file_policy_profile_create")
public class FilePolicyProfileParam implements Serializable {

    // file policy profile name
    private String profileName;

    // List of policies associated to this profile
    private List<FilePolicyParam> filePolicy;

    public FilePolicyProfileParam() {

    }

    @XmlElement(required = true, name = "profile_name")
    @Length(min = 2, max = 128)
    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    @XmlElement(required = true, name = "file_policy")
    public List<FilePolicyParam> getFilePolicies() {
        return filePolicy;
    }

    public void setFilePolicies(List<FilePolicyParam> filePolicy) {
        this.filePolicy = filePolicy;
    }

}
