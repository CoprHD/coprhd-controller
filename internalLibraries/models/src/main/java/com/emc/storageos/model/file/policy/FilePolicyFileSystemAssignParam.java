/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.io.Serializable;
import java.net.URI;

import javax.xml.bind.annotation.XmlElement;

/**
 * @author jainm15
 */
public class FilePolicyFileSystemAssignParam implements Serializable {

    private static final long serialVersionUID = 1L;
    private boolean assigntoAll;
    private URI vpool;

    public FilePolicyFileSystemAssignParam() {
        super();
    }

    /**
     * TRUE means: if policy has to be applied on all file system coming under specified vpool/project, at the time of
     * provisioning.
     * FALSE means : policy has to applied on the specific file system chosen at the time of provisioning..
     * 
     * @return
     */
    @XmlElement(name = "assign_to_all")
    public boolean getAssigntoAll() {
        return this.assigntoAll;
    }

    public void setAssigntoAll(boolean assigntoAll) {
        this.assigntoAll = assigntoAll;
    }

    @XmlElement(name = "vpool", required = true)
    public URI getVpool() {
        return this.vpool;
    }

    public void setVpool(URI vpool) {
        this.vpool = vpool;
    }
}
