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

/**
 * @author jainm15
 */
public class FilePolicyVpoolAssignParam implements Serializable {

    private static final long serialVersionUID = 1L;

    private Set<URI> assigntoVpools;

    public FilePolicyVpoolAssignParam() {
        super();
    }

    @XmlElementWrapper(name = "assign_to_vpools", required = true)
    @XmlElement(name = "vpool")
    public Set<URI> getAssigntoVpools() {
        return this.assigntoVpools;
    }

    public void setAssigntoVpools(Set<URI> assigntoVpools) {
        this.assigntoVpools = assigntoVpools;
    }

}
