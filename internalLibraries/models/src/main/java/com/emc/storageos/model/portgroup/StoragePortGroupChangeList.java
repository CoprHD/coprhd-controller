
/*
 * Copyright (c) 2018 DellEMC
 * All Rights Reserved
 */
package com.emc.storageos.model.portgroup;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Lists the potential port group for a block export port group change for
 * a specific export. Specifies for each port group in the list whether
 * or not the port group change would be allowed and if not, why.
 */
@XmlRootElement(name = "port_group_change_list")
public class StoragePortGroupChangeList {

    // A list of port groups changes.
    private List<StoragePortGroupChangeRep> portGroups;

    public StoragePortGroupChangeList() {
    }

    public StoragePortGroupChangeList(List<StoragePortGroupChangeRep> portGroups) {
        this.setPortGroups(portGroups);
    }

    /**
     * The list of port group change response instances.
     * 
     * 
     * @return The list of port group change response instances.
     */
    @XmlElement(name = "port_group_change")
    @JsonProperty("port_group_change")
    public List<StoragePortGroupChangeRep> getPortGroups() {
        if (portGroups == null) {
            portGroups = new ArrayList<StoragePortGroupChangeRep>();
        }
        return portGroups;
    }

    public void setPortGroups(List<StoragePortGroupChangeRep> portGroups) {
        this.portGroups = portGroups;
    }
}
