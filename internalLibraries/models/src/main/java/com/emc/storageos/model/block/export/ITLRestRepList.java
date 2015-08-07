/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "itls")
public class ITLRestRepList {

    private List<ITLRestRep> exportList;

    public ITLRestRepList() {
    }

    public ITLRestRepList(List<ITLRestRep> exportList) {
        this.exportList = exportList;
    }

    /**
     * A list of Initiator-Target-Lun Rest Response objects.
     * Each entry in the list represents one initiator - target connection to a volume.
     * 
     * @valid none
     */
    @XmlElement(name = "itl")
    public List<ITLRestRep> getExportList() {
        if (exportList == null) {
            exportList = new ArrayList<ITLRestRep>();
        }
        return exportList;
    }

    public void setExportList(List<ITLRestRep> exportList) {
        this.exportList = exportList;
    }

}
