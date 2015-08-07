/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.search;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "resources")
public class Resources {
    private List<TagTypedRelatedResourceRep> resource;

    public Resources() {
    }

    public Resources(List<TagTypedRelatedResourceRep> resource) {
        this.resource = resource;
    }

    public List<TagTypedRelatedResourceRep> getResource() {
        if (resource == null) {
            resource = new ArrayList<TagTypedRelatedResourceRep>();
        }
        return resource;
    }

    public void setResource(List<TagTypedRelatedResourceRep> resource) {
        this.resource = resource;
    }

}
