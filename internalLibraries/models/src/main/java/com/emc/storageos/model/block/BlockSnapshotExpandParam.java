/*
 * Copyright (c) 2018 Dell-EMC
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class defines block snapshot expansion size.
 */
@XmlRootElement(name = "snapshot_expand")
public class BlockSnapshotExpandParam {

    private String newSize;

    public BlockSnapshotExpandParam() {
    }

    public BlockSnapshotExpandParam(String newSize) {
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
