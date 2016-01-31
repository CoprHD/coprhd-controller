/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class ExportRules {

    private List<ExportRule> exportRules;

    /**
     * List of exportRules to be modified
     * 
     * 
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
