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

    private List<ExportPathParamsRestRep> exportPathParamsList;

    public ExportPathParamsBulkRep() {
    }

    public ExportPathParamsBulkRep(List<ExportPathParamsRestRep> exportPathParamsList) {
        super();
        this.exportPathParamsList = exportPathParamsList;
    }

    @XmlElement(name = "export_path_parameters")
    public List<ExportPathParamsRestRep> getExportPathParamsList() {
        return exportPathParamsList;
    }

    public void setExportPathParamsList(List<ExportPathParamsRestRep> exportPathParamsList) {
        this.exportPathParamsList = exportPathParamsList;
    }

}
