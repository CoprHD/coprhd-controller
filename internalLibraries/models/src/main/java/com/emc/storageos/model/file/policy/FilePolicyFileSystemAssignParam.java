/*
 * Copyright (c) 2017 EMC Corporation
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

    private URI vpool;

    public FilePolicyFileSystemAssignParam() {
        super();
    }

    @XmlElement(name = "vpool")
    public URI getVpool() {
        return this.vpool;
    }

    public void setVpool(URI vpool) {
        this.vpool = vpool;
    }
}
