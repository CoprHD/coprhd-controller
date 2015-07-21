/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.healthmonitor;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Rest response for diagnostics.
 */
@XmlRootElement(name = "diagnostics")
public class DiagnosticsRestRep {

    private List<NodeDiagnostics> nodeDiagnosticsList;

    public DiagnosticsRestRep() {}
    
    public DiagnosticsRestRep(List<NodeDiagnostics> nodeDiagnosticsList) {
        super();
        this.nodeDiagnosticsList = nodeDiagnosticsList;
    }

    @XmlElementWrapper(name = "node_diagnostics_list")
    @XmlElement(name = "node_diagnostics")
    public List<NodeDiagnostics> getNodeDiagnosticsList() {
        if (nodeDiagnosticsList == null) {
            nodeDiagnosticsList = new ArrayList<NodeDiagnostics>();
        }
        return nodeDiagnosticsList;
    }

    public void setNodeDiagnosticsList(List<NodeDiagnostics> nodeDiagnosticsList) {
        this.nodeDiagnosticsList = nodeDiagnosticsList;
    }
}
