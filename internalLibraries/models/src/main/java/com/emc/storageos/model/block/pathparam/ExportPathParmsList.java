/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.pathparam;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "path_params_list")
public class ExportPathParmsList {

    private List<NamedRelatedResourceRep> exportPathParmsList;

    public ExportPathParmsList() {

    }

    public ExportPathParmsList(List<NamedRelatedResourceRep> exportPathParmsList) {
        this.exportPathParmsList = exportPathParmsList;
    }

    @XmlElement(name = "export_path_parameters")
    public List<NamedRelatedResourceRep> getExportPathParmsList() {
        return exportPathParmsList;
    }

    public void setExportPathParmsList(List<NamedRelatedResourceRep> exportPathParmsList) {
        this.exportPathParmsList = exportPathParmsList;
    }

}
