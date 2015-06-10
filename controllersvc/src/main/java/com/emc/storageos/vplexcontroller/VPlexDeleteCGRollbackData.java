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
package com.emc.storageos.vplexcontroller;

import java.io.Serializable;
import java.net.URI;

/**
 * Captures the data necessary to recreate a VPLEX consistency Group
 * after a workflow failure.
 */
public class VPlexDeleteCGRollbackData implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    String cgName;
    String clusterName;
    Boolean isDistributed;
    URI vplexSystemURI;
    
    public VPlexDeleteCGRollbackData() {}
    
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
