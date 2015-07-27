/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

    public ExportBlockParam() {}
    
    public ExportBlockParam(URI id, Integer lun) {
        this.id = id;
        this.lun = lun;
    }

    /**
     * Block Export parameters -
     * Volume or snapshot.
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
