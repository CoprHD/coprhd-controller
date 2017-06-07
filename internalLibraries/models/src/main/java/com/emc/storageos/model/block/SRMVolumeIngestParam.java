package com.emc.storageos.model.block;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "srm_volume_ingest")
public class SRMVolumeIngestParam {
    
    private URI storagsystem;
    private URI project;
    
    @XmlElement(required = true)
    public URI getStoragsystem() {
        return storagsystem;
    }
    
    public void setStoragsystem(URI storagsystem) {
        this.storagsystem = storagsystem;
    }
    
    @XmlElement(required = true)
    public URI getProject() {
        return project;
    }
    
    public void setProject(URI project) {
        this.project = project;
    }

}
