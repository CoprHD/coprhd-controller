/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "path_params")
public class ExportPathParametersList {

    private List<NamedRelatedResourceRep> pathParamsList;

    public ExportPathParametersList() {

    }

    public ExportPathParametersList(List<NamedRelatedResourceRep> pathParamsList) {
        this.pathParamsList = pathParamsList;
    }

    public List<NamedRelatedResourceRep> getPathParamsList() {
        if (pathParamsList == null) {
            pathParamsList = new ArrayList<NamedRelatedResourceRep>();
        }
        return pathParamsList;
    }

    public void setPathParamsList(List<NamedRelatedResourceRep> pathParamsList) {
        this.pathParamsList = pathParamsList;
    }

}
