/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.network;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * This contains a list of the vsan or fabric names seen by the NetworkSystem.
 */
@XmlRootElement(name="san_fabrics")
public class Fabrics {
    
    private List<String> fabricIds = new ArrayList<String>();

    public Fabrics() {}
    
    public Fabrics(List<String> fabricIds) {
        this.fabricIds = fabricIds;
    }

    /**
     * A list of fabric names discovered by the NetworkSystem.
     * @valid none
     */
    @XmlElement(name="fabric")
    public List<String> getFabricIds() {
        if (fabricIds == null) {
            fabricIds = new ArrayList<String>();
        }
        return fabricIds;
    }

    public void setFabricIds(List<String> fabricIds) {
        this.fabricIds = fabricIds;
    }

}
