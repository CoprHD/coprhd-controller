/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.net.URI;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RestLinkRep;

/**
 * 
 * @author jainm15
 *
 */
@XmlRootElement(name = "file_policy_assignment")
public class FilePolicyAssignResp extends NamedRelatedResourceRep {

    private Set<String> assignedResources;
    private String appliedAtLevel;

    public FilePolicyAssignResp() {
    }

    public FilePolicyAssignResp(URI id, RestLinkRep selfLink, String name, String appliedAtLevel, Set<String> assignedResources) {
        super(id, selfLink, name);
        this.appliedAtLevel = appliedAtLevel;
        this.assignedResources = assignedResources;
    }

    public FilePolicyAssignResp(URI id, RestLinkRep selfLink, String name, String appliedAtLevel) {
        super(id, selfLink, name);
        this.appliedAtLevel = appliedAtLevel;

    }

    @XmlElement(name = "applied_at_level")
    public String getAppliedAtLevel() {
        return this.appliedAtLevel;
    }

    public void setAppliedAtLevel(String appliedAtLevel) {
        this.appliedAtLevel = appliedAtLevel;
    }

    @XmlElementWrapper(name = "assign_to_resources")
    @XmlElement(name = "assign_to_resource")
    public Set<String> getAssignedResources() {
        return this.assignedResources;
    }

    public void setAssignedResources(Set<String> assignedResources) {
        this.assignedResources = assignedResources;
    }

}
