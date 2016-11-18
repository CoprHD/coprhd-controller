/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 * @author jainm15
 */
public class FilePolicyVpoolAssignParam implements Serializable {

    private static final long serialVersionUID = 1L;
    private boolean assigntoAll;
    private Set<String> assigntoVpools;

    @XmlElement(name = "assign_to_all")
    public boolean isAssigntoAll() {
        return this.assigntoAll;
    }

    public void setAssigntoAll(boolean assigntoAll) {
        this.assigntoAll = assigntoAll;
    }

    @XmlElementWrapper(name = "assign_to_vpools", required = true)
    @XmlElement(name = "vpool")
    public Set<String> getAssigntoVpools() {
        return this.assigntoVpools;
    }

    public void setAssigntoVpools(Set<String> assigntoVpools) {
        this.assigntoVpools = assigntoVpools;
    }

}
