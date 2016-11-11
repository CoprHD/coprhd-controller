/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "port_allocate_preview")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class PortAllocatePreviewRestRep {
    
    private List<InitiatorPortMapRestRep> addedPaths;
    private List<InitiatorPortMapRestRep> removedPaths;
    private List<NamedRelatedResourceRep> affectedExportGroups;
    
    @XmlElementWrapper(name = "affected_export_groups")
    public List<NamedRelatedResourceRep> getAffectedExportGroups() {
        if (affectedExportGroups == null) {
            affectedExportGroups = new ArrayList<NamedRelatedResourceRep>();
        }
        return affectedExportGroups;
    }

    @XmlElementWrapper(name = "added_paths") 
    public List<InitiatorPortMapRestRep> getAddedPaths() {
        if (addedPaths == null) {
            addedPaths = new ArrayList<InitiatorPortMapRestRep>();
        }
        return addedPaths;
    }
    
    @XmlElementWrapper(name = "removed_paths") 
    public List<InitiatorPortMapRestRep> getRemovedPaths() {
        if (removedPaths == null) {
            removedPaths = new ArrayList<InitiatorPortMapRestRep>();
        }
        return removedPaths;
    }
    
    public class InitiatorPortMapRestRepComparator implements Comparator<InitiatorPortMapRestRep> {
        @Override
        public int compare(InitiatorPortMapRestRep arg0, InitiatorPortMapRestRep arg1) {
            if (arg0.getInitiator() == null || arg0.getInitiator().getHostName() == null || arg0.getInitiator().getInitiatorPort() == null
                || arg1.getInitiator() == null || arg1.getInitiator().getHostName() == null || arg1.getInitiator().getInitiatorPort() == null) {
                return 0;
            }
            if (arg0.getInitiator().getHostName().equals(arg1.getInitiator().getHostName())) {
                return (arg0.getInitiator().getInitiatorPort().compareTo(arg1.getInitiator().getInitiatorPort()));
            }
            return (arg0.getInitiator().getHostName().compareTo(arg1.getInitiator().getHostName()));
        }
    }

}
