/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class ExportRules implements Serializable {

    /**
     * Class Name based Hashed version Id.
     */
    private static final long serialVersionUID = 4764715771932826603L;
    private List<ExportRule> exportRules;

    /**
     * List of exportRules to be modified
     * 
     * @valid none
     */
    @XmlElementWrapper(name = "exportRules")
    @XmlElement(name = "exportRule")
    public List<ExportRule> getExportRules() {
        return exportRules;
    }

    public void setExportRules(List<ExportRule> exportRules) {
        this.exportRules = exportRules;
    }

}
