/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.block.export;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;

/**
 * Common block export parameter representing either volume or snapshot with its mapping lun
 */
@XmlRootElement(name = "export_block")
public class ExportBlockParam {

    private URI id;
    private Integer lun;

    public ExportBlockParam() {
    }

    public ExportBlockParam(URI id, Integer lun) {
        this.id = id;
        this.lun = lun;
    }

    /**
     * Block Export parameters -
     * Volume or snapshot.
     * 
     * @valid example: a valid URI
     */
    @XmlElement(required = true)
    public URI getId() {
        return id;
    }

    public void setId(URI id) {
        this.id = id;
    }

    /**
     * Corresponding (optional) mapped LUN
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
