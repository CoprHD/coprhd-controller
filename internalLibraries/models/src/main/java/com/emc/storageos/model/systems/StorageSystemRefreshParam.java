package com.emc.storageos.model.systems;


import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "storage_system_refresh")
public class StorageSystemRefreshParam {
    
    private List<URI> masks;

    @XmlElementWrapper(required = false, name = "masks")
    @XmlElement(required = false, name = "mask")
    public List<URI> getMasks() {
        if (masks == null) {
            masks = new ArrayList<URI>();
        }
        return masks;
    }

    public void setMasks(List<URI> masks) {
        this.masks = masks;
    }
    
    
}
