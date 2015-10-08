/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.responses;
import java.io.Serializable;
import java.util.List;

/**
 * Parameters necessary to create/update a consistency group, given newly created volumes.
 * Need enough information to be able to export the volumes to the RPAs and to create the CG.
 * 
 */
@SuppressWarnings("serial")
public class GetCGsResponse implements Serializable {
    private boolean isJunitTest;
    // Name of the CG Group
    private String cgName;
    // CG ID
    private long cgId;
    // Top-level policy for the CG
    public GetPolicyResponse cgPolicy;
    // List of copies
    private List<GetCopyResponse> copies;
    // List of replication sets that make up this consistency group.
    private List<GetRSetResponse> rsets;

    public GetCGsResponse() {
        isJunitTest = false;
    }

    public boolean isJunitTest() {
        return isJunitTest;
    }

    public void setJunitTest(boolean isJunitTest) {
        this.isJunitTest = isJunitTest;
    }

    public String getCgName() {
        return cgName;
    }

    public void setCgName(String cgName) {
        this.cgName = cgName;
    }

    public long getCgId() {
        return cgId;
    }

    public void setCgId(long cgId) {
        this.cgId = cgId;
    }

    public List<GetCopyResponse> getCopies() {
        return copies;
    }

    public void setCopies(List<GetCopyResponse> copies) {
        this.copies = copies;
    }

    public List<GetRSetResponse> getRsets() {
        return rsets;
    }

    public void setRsets(List<GetRSetResponse> rsets) {
        this.rsets = rsets;
    }

    // The top-level CG policy objects
    public static class GetPolicyResponse implements Serializable {
        public String copyMode;
        public Long rpoValue;
        public String rpoType;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("\ncgName: " + cgName);
        sb.append("\n---------------\n");
        if (copies != null) {
            for (GetCopyResponse copy : copies) {
                sb.append(copy);
                sb.append("\n");
            }
        }
        sb.append("\n---------------\n");
        if (rsets != null) {
            for (GetRSetResponse rset : rsets) {
                sb.append(rset);
                sb.append("\n");
            }
        }
        sb.append("\n---------------\n");
        return sb.toString();
    }
}
