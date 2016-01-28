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
    private String accessState; // LOGGED_ACCESS, NO_ACCESS, etc
    private boolean enabled;
    private String accessedImage;
    private GetCopyRole role;

    // Every copy has three identifiers, and you need all three to be unique across all CG's copies
    private long cgId;       // The ID of the CG it belongs to
    // You need the following two identifiers to be unique WITHIN a CG
    private long clusterId;  // The global ID of the cluster the copy lives on
    private long copyId;     // The ID of the copy

    // Access state info of RP Copy
    public static enum GetCopyAccessStateResponse implements Serializable {
        DIRECT_ACCESS,
        LOGGED_ACCESS,
        VIRTUAL_ACCESS,
        ENABLING_LOGGED_ACCESS,
        ENABLING_VIRTUAL_ACCESS,
        VIRTUAL_ACCESS_ROLLING_IMAGE,
        LOGGED_ACCESS_ROLL_COMPLETE,
        NO_ACCESS,
        NO_ACCESS_UNDOING_WRITES,
        NO_ACCESS_SPACE_FULL,
        NO_ACCESS_JOURNAL_PRESERVED,
        NO_ACCESS_BFS_GROUP,
        VIRTUAL_ACCESS_CANNOT_ROLL_IMAGE,
        UNKNOWN;
    }
    
    // Role of RP Copy
    public static enum GetCopyRole implements Serializable {
        ACTIVE_PRODUCTION,
        STANDBY_PRODUCTION,
        TARGET,
        UNKNOWN;
    }

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

    public String getAccessState() {
        return accessState;
    }

    public void setAccessState(String accessState) {
        this.accessState = accessState;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAccessedImage() {
        return accessedImage;
    }

    public void setAccessedImage(String accessedImage) {
        this.accessedImage = accessedImage;
    }
    
    public GetCopyRole getRole() {
        if (this.role == null) {
            this.role = GetCopyRole.UNKNOWN;
        }
        return role;
    }

    public void setRole(GetCopyRole role) {
        this.role = role;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("\nCopy: " + name);
        sb.append("\nProduction: " + production);
        sb.append("\nRole: " + this.getRole().toString());
        sb.append("\nEnabled: " + enabled);
        sb.append("\nAccess State: " + accessState);
        if (accessedImage != null) {
            sb.append(", Accessed Image: " + accessedImage);
        }
        sb.append("\nCGID: " + cgId + ", Cluster ID: " + String.format("0x%x", clusterId) + ", Copy ID: " + copyId);
        if (journals != null) {
            sb.append("\nJournals:");
            for (GetVolumeResponse volume : journals) {
                sb.append(volume.toString());
            }
        }
        return sb.toString();
    }
}
