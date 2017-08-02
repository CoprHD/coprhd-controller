/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.slf4j.Logger;

@XmlRootElement(name="initiator_path")
public class InitiatorPathParam {
    private URI initiator;
    private List<URI> storagePorts;
    
    public InitiatorPathParam() {
    }

    public InitiatorPathParam(URI initiator) {
        this.initiator = initiator;
    }
    
    @XmlElement(required = true)
    public URI getInitiator() {
        return initiator;
    }

    public void setInitiator(URI initiator) {
        this.initiator = initiator;
    }
    
    @XmlElementWrapper(name = "storage_ports", required = true)
    @XmlElement(name = "storage_port")
    public List<URI> getStoragePorts() {
        if (storagePorts == null) {
            storagePorts = new ArrayList<URI>();
        }
        return storagePorts;
    }
    public void setStoragePorts(List<URI> storagePorts) {
        this.storagePorts = storagePorts;
    }
    
    public void log(Logger log) {
    	log.info(String.format("%s -> %s", initiator, storagePorts));
    }
}
