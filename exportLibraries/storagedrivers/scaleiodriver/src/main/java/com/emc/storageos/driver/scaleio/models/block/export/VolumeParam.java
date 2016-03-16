/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.scaleio.models.block.export;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;

/**
 * Representation of storage to be added to block export.
 */
@XmlRootElement(name = "volume_param")
public class VolumeParam {
    private static final int LUN_UNASSIGNED = -1;

    private URI id;
    private Integer lun = LUN_UNASSIGNED;

    public VolumeParam() {
    }

    public VolumeParam(URI id) {
        this.id = id;
    }

    /**
     * URI of volume or volume snapshot to be added to the block export.
     * This volume or snapshot must belong to the same virtual array as
     * the block export.
     * 
     * @valid example: URI of the volume or snapshot to be added to the block export.
     */
    @XmlElement(required = true)
    public URI getId() {
        return id;
    }

    public void setId(URI id) {
        this.id = id;
    }

    /**
     * Logical Unit Number for this volume or snapshot as seen by the initiators.
     * This is an optional parameter. If not supplied, LUN number is auto-assigned.
     * Set this only if the volume is to be visible to all initiators in a cluster
     * with the same LUN number.
     * 
     * @valid none
     */
    @XmlElement(required = false)
    public Integer getLun() {
        return lun;
    }

    public void setLun(Integer lun) {
        this.lun = lun;
    }
}
