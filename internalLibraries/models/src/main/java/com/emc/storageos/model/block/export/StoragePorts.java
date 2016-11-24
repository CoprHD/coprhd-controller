package com.emc.storageos.model.block.export;

import java.io.Serializable;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

public class StoragePorts implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -7348637714376710785L;
    private List<String> storagePorts;
    
    
    /**
     * List of Storage Ports to be modified
     * 
     */
    @XmlElementWrapper(name="storage_ports", required=false)
    @XmlElement(name = "storage_port")
    public List<String> getStoragePorts() {
        return storagePorts;
    }
    public void setStoragePorts(List<String> storagePorts) {
        this.storagePorts = storagePorts;
    }
    
    

}
