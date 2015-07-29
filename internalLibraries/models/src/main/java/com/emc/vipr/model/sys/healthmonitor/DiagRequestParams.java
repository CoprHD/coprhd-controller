/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.healthmonitor;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Request parameters for diagnostics API.
 */
@XmlRootElement
public class DiagRequestParams {

    private boolean verbose;

    public DiagRequestParams() {
    }

    public DiagRequestParams(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}
