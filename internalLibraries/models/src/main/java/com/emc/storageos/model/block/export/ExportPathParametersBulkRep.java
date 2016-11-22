/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_path_param")
public class ExportPathParametersBulkRep extends BulkRestRep {

    private List<ExportPathParametersRestRep> exportPathParamsList;

    public ExportPathParametersBulkRep() {

    }

    public ExportPathParametersBulkRep(List<ExportPathParametersRestRep> exportPathParamsList) {
        super();
        this.exportPathParamsList = exportPathParamsList;
    }

    @XmlElement(name = "path_param")
    public List<ExportPathParametersRestRep> getExportPathParamsList() {
        return exportPathParamsList;
    }

    public void setExportPathParamsList(List<ExportPathParametersRestRep> exportPathParamsList) {
        this.exportPathParamsList = exportPathParamsList;
    }

}
