/*
 * Copyright (c) 2015 EMC Corporation
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
    private boolean unassignFromAll;
    private boolean forceUnassign = false;
    private Set<URI> unassignfrom;

    public FilePolicyUnAssignParam() {
        super();
    }

    @XmlElement(name = "unassign_from_all")
    public boolean getUnassignFromAll() {
        return unassignFromAll;
    }

    public void setUnassignFromAll(boolean unassignFromAll) {
        this.unassignFromAll = unassignFromAll;
    }

    @XmlElement(name = "force_unassign")
    public boolean getForceUnassign() {
        return forceUnassign;
    }

    public void setForceUnassign(boolean forceUnassign) {
        this.forceUnassign = forceUnassign;
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
