/*
 * Copyright (c) 2015 EMC Corporation
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

    public VolumeExpandParam() {
    }

    public VolumeExpandParam(String newSize) {
        this.newSize = newSize;
    }

    /**
     * This parameter specifies the volume expansion
     * size.
     * Valid value:
     *      Supported size formats: TB, GB, MB, B
     *      Default format is size in bytes
     */
    @XmlElement(required = true, name = "new_size")
    public String getNewSize() {
        return newSize;
    }

    public void setNewSize(String newSize) {
        this.newSize = newSize;
    }

}
