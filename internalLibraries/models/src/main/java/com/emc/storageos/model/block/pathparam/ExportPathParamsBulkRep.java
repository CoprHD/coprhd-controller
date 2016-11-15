/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.pathparam;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_export_path_params")
public class ExportPathParamsBulkRep extends BulkRestRep {

    private List<ExportPathParmsRestRep> exportPathParamsList;

    public ExportPathParamsBulkRep() {
    }

    public ExportPathParamsBulkRep(List<ExportPathParmsRestRep> exportPathParamsList) {
        super();
        this.exportPathParamsList = exportPathParamsList;
    }

    @XmlElement(name = "export_path_parameters")
    public List<ExportPathParmsRestRep> getExportPathParamsList() {
        return exportPathParamsList;
    }

    public void setExportPathParamsList(List<ExportPathParmsRestRep> exportPathParamsList) {
        this.exportPathParamsList = exportPathParamsList;
    }

}
