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
public class GetCGsResponse implements Serializable {
    private static final long serialVersionUID = 6619541304723730047L;

    private boolean isJunitTest;
    // Name of the CG Group
    private String cgName;
    // CG ID
    private long cgId;
    // Top-level policy for the CG
    public GetPolicyResponse cgPolicy;
    // Overall health/state information
    public GetCGStateResponse cgState;
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

    // Various top-level state information of an RP CG
    public static enum GetCGStateResponse implements Serializable {
        UNKNOWN("0"),
        HEALTHY("1"),
        UNHEALTHY_PAUSED_OR_DISABLED("2"),
        UNHEALTHY_ERROR("3"),
        UNHEALTHY_ACCESSED("4");

        private final String state;

        GetCGStateResponse(String state) {
            this.state = state;
        }

        public String getState() {
            return state;
        }

        private static final GetCGStateResponse[] copyOfValues = values();

        public static String getGetCGStateResponseDisplayName(String state) {
            for (GetCGStateResponse stateValue : copyOfValues) {
                if (stateValue.getState().contains(state)) {
                    return stateValue.name();
                }
            }
            return GetCGStateResponse.UNKNOWN.name();
        }
    }
    
    // The top-level CG policy objects
    public static class GetPolicyResponse implements Serializable {
        private static final long serialVersionUID = -7315028258346027172L;
        
        public Boolean synchronous;
        public Long rpoValue;
        public String rpoType;
        
        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append("synchronous: " + synchronous);
            sb.append(", rpoValue: " + rpoValue);
            sb.append(", rpoType: " + rpoType);
            return sb.toString();
        }
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("\ncgName: " + cgName);
        sb.append("\ncgId: " + cgId);
        sb.append("\ncgPolicy: " + cgPolicy);
        sb.append("\nstate: " + (cgState == null ? "None" : cgState.toString()));
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
