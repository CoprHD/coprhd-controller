/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.responses;

import java.io.Serializable;
import java.util.List;

// Each RP copy has a list of Journal volumes and a name
@SuppressWarnings("serial")
public class GetCopyResponse implements Serializable {
    private String name;
    private List<GetVolumeResponse> journals;
    private boolean production;
    
    // Every copy has three identifiers, and you need all three to be unique across all CG's copies
    private long cgId;       // The ID of the CG it belongs to
    // You need the following two identifiers to be unique WITHIN a CG
    private long clusterId;  // The global ID of the cluster the copy lives on
    private long copyId;     // The ID of the copy

    public GetCopyResponse() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<GetVolumeResponse> getJournals() {
        return journals;
    }

    public void setJournals(List<GetVolumeResponse> journals) {
        this.journals = journals;
    }

    public boolean isProduction() {
        return production;
    }

    public void setProduction(boolean production) {
        this.production = production;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("\nCopy: " + name);
        sb.append("\nProduction: " + production);
        sb.append("\nCGID: " + cgId + ", Cluster ID: " + String.format("0x%x", clusterId) + ", Copy ID: " + copyId);
        if (journals != null) {
            sb.append("\nJournals:");
            for (GetVolumeResponse volume : journals) {
                sb.append(volume.toString());
            }
        }
        return sb.toString();
    }

    public long getCgId() {
        return cgId;
    }

    public void setCgId(long cgId) {
        this.cgId = cgId;
    }

    public long getClusterId() {
        return clusterId;
    }

    public void setClusterId(long clusterId) {
        this.clusterId = clusterId;
    }

    public long getCopyId() {
        return copyId;
    }

    public void setCopyId(long copyId) {
        this.copyId = copyId;
    }
}
