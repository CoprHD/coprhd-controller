/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.net.URI;

import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RestLinkRep;

/**
 * 
 * @author jainm15
 *
 */
@XmlRootElement(name = "file_policy")
public class FilePolicyCreateResp extends NamedRelatedResourceRep {
    private String applyAt;

    public FilePolicyCreateResp() {
    }

    public FilePolicyCreateResp(URI id, RestLinkRep selfLink, String name) {
        super(id, selfLink, name);

    }
}
