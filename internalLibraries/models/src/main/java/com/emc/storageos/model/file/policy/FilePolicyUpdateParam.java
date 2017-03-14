/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "file_policy_update")
public class FilePolicyUpdateParam extends FilePolicyParam implements Serializable {
    private static final long serialVersionUID = 1L;
}
