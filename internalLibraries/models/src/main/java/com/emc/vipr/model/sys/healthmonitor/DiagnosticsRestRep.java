/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

    public DiagnosticsRestRep() {
    }

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
