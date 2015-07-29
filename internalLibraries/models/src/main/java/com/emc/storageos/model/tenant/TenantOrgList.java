/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.tenant;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "subtenants")
public class TenantOrgList {
    private List<NamedRelatedResourceRep> subtenants;

    public TenantOrgList() {
    }

    public TenantOrgList(List<NamedRelatedResourceRep> subtenants) {
        this.subtenants = subtenants;
    }

    /**
     * List of this tenant's subtenants.
     * 
     * @valid none
     * @return List of NamedRelatedResourceRep
     */
    @XmlElement(name = "subtenant")
    public List<NamedRelatedResourceRep> getSubtenants() {
        if (subtenants == null) {
            subtenants = new ArrayList<NamedRelatedResourceRep>();
        }
        return subtenants;
    }

    public void setSubtenants(List<NamedRelatedResourceRep> subtenants) {
        this.subtenants = subtenants;
    }
}
