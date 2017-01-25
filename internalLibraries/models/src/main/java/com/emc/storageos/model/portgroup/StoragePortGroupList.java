/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.portgroup;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "storage_port_groups")
public class StoragePortGroupList {
    private List<NamedRelatedResourceRep> portGroups;

    public StoragePortGroupList() {
    }
    
    public StoragePortGroupList(List<NamedRelatedResourceRep> portGroups) {
        this.portGroups = portGroups;
    }

    @XmlElement(name = "storage_port_group")
    public List<NamedRelatedResourceRep> getPortGroups() {
        if (portGroups == null) {
            portGroups = new ArrayList<NamedRelatedResourceRep> ();
        }
        return portGroups;
    }

    public void setPortGroups(List<NamedRelatedResourceRep> portGroups) {
        this.portGroups = portGroups;
    }

}
