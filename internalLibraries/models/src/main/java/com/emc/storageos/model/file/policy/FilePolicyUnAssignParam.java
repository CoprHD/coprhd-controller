/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.io.Serializable;
import java.net.URI;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author jainm15
 */
@XmlRootElement(name = "unassign_file_policy")
public class FilePolicyUnAssignParam implements Serializable {

    private static final long serialVersionUID = 1L;
    private Set<URI> unassignfrom;

    public FilePolicyUnAssignParam() {
        super();
    }

    @XmlElementWrapper(name = "unassign_from")
    @XmlElement(name = "URI")
    public Set<URI> getUnassignfrom() {
        return unassignfrom;
    }

    public void setUnassignfrom(Set<URI> unassignfrom) {
        this.unassignfrom = unassignfrom;
    }
}
