/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author jainm15
 */
@XmlRootElement(name = "unassign_file_policy")
public class FilePolicyUnAssignParam implements Serializable {

    private static final long serialVersionUID = 1L;

    public FilePolicyUnAssignParam() {
        super();
    }
}
