/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "export_path_policies")
public class ExportPathPoliciesList {

    private List<NamedRelatedResourceRep> pathPoliciesList;

    public ExportPathPoliciesList() {

    }

    public ExportPathPoliciesList(List<NamedRelatedResourceRep> pathParamsList) {
        this.pathPoliciesList = pathParamsList;
    }

    @XmlElement(name = "export_path_policies")
    public List<NamedRelatedResourceRep> getPathParamsList() {
        if (pathPoliciesList == null) {
            pathPoliciesList = new ArrayList<NamedRelatedResourceRep>();
        }
        return pathPoliciesList;
    }

    public void setPathParamsList(List<NamedRelatedResourceRep> pathPoliciesList) {
        this.pathPoliciesList = pathPoliciesList;
    }

}
