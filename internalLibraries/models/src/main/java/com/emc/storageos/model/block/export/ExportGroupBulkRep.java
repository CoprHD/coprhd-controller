/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import com.emc.storageos.model.BulkRestRep;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_exportgroups")
public class ExportGroupBulkRep extends BulkRestRep {
    private List<ExportGroupRestRep> exports;

    /**
     * List of export groups.
     * 
     * @valid none
     */
    @XmlElement(name = "exportgroup")
    public List<ExportGroupRestRep> getExports() {
        if (exports == null) {
            exports = new ArrayList<ExportGroupRestRep>();
        }
        return exports;
    }

    public void setExports(List<ExportGroupRestRep> exports) {
        this.exports = exports;
    }

    public ExportGroupBulkRep() {
    }

    public ExportGroupBulkRep(List<ExportGroupRestRep> exports) {
        this.exports = exports;
    }
}
