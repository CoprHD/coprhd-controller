/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.slf4j.Logger;

@XmlRootElement(name = "replace_port")
public class PortReplacementParam {
    private URI oldPort;
    private URI newPort;

    /**
     * Specify old port to be replaced
     */
    @XmlElement(name = "old_port", required = true)
    public URI getOldPort() {
        return oldPort;
    }

    public void setOldPort(URI oldPort) {
        this.oldPort = oldPort;
    }

    @XmlElement(name = "new_port", required = true)
    public URI getNewPort() {
        return newPort;
    }

    public void setNewPort(URI newPort) {
        this.newPort = newPort;
    }
    
    public void log(Logger log) {
        log.info(String.format("old port %s -> new port %s", oldPort, newPort));
    }

}
