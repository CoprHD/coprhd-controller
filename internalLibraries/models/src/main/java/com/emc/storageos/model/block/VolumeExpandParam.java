/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class defines volume expansion size.
 */
@XmlRootElement(name = "volume_expand")
public class VolumeExpandParam {

    private String newSize;

    public VolumeExpandParam() {}
            
    public VolumeExpandParam(String newSize) {
        this.newSize = newSize;
    }

    /**
     * This parameter specifies the volume expansion
     * size.
     * @valid Supported size formats: TB, GB, MB, B
     * @valid Default format is size in bytes
     * @valid example: 100GB, 614400000, 614400000B
     */
    @XmlElement(required = true, name = "new_size")
    public String getNewSize() {
        return newSize;
    }

    public void setNewSize(String newSize) {
        this.newSize = newSize;
    }
  
}
