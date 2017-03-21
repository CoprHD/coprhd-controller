/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller;

import java.io.Serializable;
import java.net.URI;

/**
 * Captures the data necessary to recreate a VPLEX consistency Group
 * after a workflow failure.
 */
public class VPlexCGRollbackData implements Serializable {

    private static final long serialVersionUID = 1L;

    String cgName;
    String clusterName;
    Boolean isDistributed;
    URI vplexSystemURI;

    public VPlexCGRollbackData() {
    }

    public void setCgName(String val) {
        cgName = val;
    }

    public String getCgName() {
        return cgName;
    }

    public void setClusterName(String val) {
        clusterName = val;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setIsDistributed(Boolean val) {
        isDistributed = val;
    }

    public Boolean getIsDistributed() {
        return isDistributed;
    }

    public void setVplexSystemURI(URI val) {
        vplexSystemURI = val;
    }

    public URI getVplexSystemURI() {
        return vplexSystemURI;
    }
}
