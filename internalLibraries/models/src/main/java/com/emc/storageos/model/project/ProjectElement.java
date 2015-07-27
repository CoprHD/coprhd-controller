/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.project;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RestLinkRep;

import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;

@XmlRootElement(name = "tenant_project")
public class ProjectElement extends NamedRelatedResourceRep {

    public ProjectElement() {}

    public ProjectElement(URI id, RestLinkRep selfLink, String name) {
        super(id, selfLink, name);
    }
}
