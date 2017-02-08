/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.host.InitiatorRestRep;

@XmlRootElement(name = "initiator_port_map")
public class InitiatorPortMapRestRep {
    private InitiatorRestRep initiator;
    private List <NamedRelatedResourceRep> storagePorts;
    
    @XmlElement(name = "initiator")
    public InitiatorRestRep getInitiator() {
        return initiator;
    }
    public void setInitiator(InitiatorRestRep initiator) {
        this.initiator = initiator;
    }
    
    @XmlElementWrapper(name = "storage_ports", required = false)
    @XmlElement(name = "storage_port")
    public List<NamedRelatedResourceRep> getStoragePorts() {
        if (storagePorts == null) {
            storagePorts = new ArrayList<NamedRelatedResourceRep>();
        }
        Collections.sort(storagePorts, new NamedRelatedResourceRepComparator());
        return storagePorts;
    }
    public void setStoragePorts(List<NamedRelatedResourceRep> storagePorts) {
        this.storagePorts = storagePorts;
    }
    
    public class NamedRelatedResourceRepComparator implements Comparator<NamedRelatedResourceRep> {
        @Override
        public int compare(NamedRelatedResourceRep o1, NamedRelatedResourceRep o2) {
           if (o1.getName() == null && o2.getName() == null) {
               return 0;
           }
           if (o1.getName() == null) {
               return -1;
           }
           if (o2.getName() == null) {
               return 1;
           }
           return(o1.getName().compareTo(o2.getName()));
        }
    }
}
