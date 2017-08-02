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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.slf4j.Logger;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "export_paths_adjustment_preview")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ExportPathsAdjustmentPreviewRestRep {
    
    private String storageSystem;
    private List<InitiatorPortMapRestRep> adjustedPaths;
    private List<InitiatorPortMapRestRep> removedPaths;
    private List<NamedRelatedResourceRep> affectedExportGroups;
    
    @XmlElementWrapper(name = "affected_export_groups")
    public List<NamedRelatedResourceRep> getAffectedExportGroups() {
        if (affectedExportGroups == null) {
            affectedExportGroups = new ArrayList<NamedRelatedResourceRep>();
        }
        return affectedExportGroups;
    }

    @XmlElementWrapper(name = "adjusted_paths") 
    public List<InitiatorPortMapRestRep> getAdjustedPaths() {
        if (adjustedPaths == null) {
            adjustedPaths = new ArrayList<InitiatorPortMapRestRep>();
        }
        return adjustedPaths;
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
    
    public void logResponse(Logger log) {
    	log.info("Path Adjustment Preview : adjustedPaths");
    	for (InitiatorPortMapRestRep entry : adjustedPaths) {
    		log.info(String.format("Host %s initiator %s (%s): ", 
    			entry.getInitiator().getHostName(), entry.getInitiator().getInitiatorPort(), entry.getInitiator().getId()));
    		StringBuilder buffer = new StringBuilder();
    		for (NamedRelatedResourceRep aPort : entry.getStoragePorts()) {
    			buffer.append(String.format("%s (%s)   ", aPort.getName(), aPort.getId()));
    		}
    		log.info("Ports: " + buffer.toString());
    	}
    	log.info("Path Adjustment Preview : removedPaths");
        if (removedPaths != null) {
    	    for (InitiatorPortMapRestRep entry : removedPaths) {
    		log.info(String.format("Host %s initiator %s (%s): ", 
    			entry.getInitiator().getHostName(), entry.getInitiator().getInitiatorPort(), entry.getInitiator().getId()));
    		StringBuilder buffer = new StringBuilder();
    		for (NamedRelatedResourceRep aPort : entry.getStoragePorts()) {
    			buffer.append(String.format("%s (%s)   ", aPort.getName(), aPort.getId()));
    		}
    		log.info("Ports: " + buffer.toString());
    	    }
        }
    }

    @XmlElement(name = "storage_system")
    public String getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(String storageSystem) {
        this.storageSystem = storageSystem;
    }

}
