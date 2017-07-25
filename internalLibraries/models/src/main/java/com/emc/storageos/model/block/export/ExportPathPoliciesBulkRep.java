/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_export_path_policies")
public class ExportPathPoliciesBulkRep extends BulkRestRep {

    private List<ExportPathPolicyRestRep> exportPathParamsList;

    public ExportPathPoliciesBulkRep() {

    }

    public ExportPathPoliciesBulkRep(List<ExportPathPolicyRestRep> exportPathParamsList) {
        super();
        this.exportPathParamsList = exportPathParamsList;
    }

    @XmlElement(name = "path_param")
    public List<ExportPathPolicyRestRep> getExportPathParamsList() {
        return exportPathParamsList;
    }

    public void setExportPathParamsList(List<ExportPathPolicyRestRep> exportPathParamsList) {
        this.exportPathParamsList = exportPathParamsList;
    }

}
