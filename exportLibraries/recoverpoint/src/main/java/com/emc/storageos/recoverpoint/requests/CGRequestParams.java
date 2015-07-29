/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.requests;

/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 **/
import java.io.Serializable;
import java.net.URI;
import java.util.List;

/**
 * Parameters necessary to create/update a consistency group, given newly created volumes.
 * Need enough information to be able to export the volumes to the RPAs and to create the CG.
 * 
 */
@SuppressWarnings("serial")
public class CGRequestParams implements Serializable {
    private boolean isJunitTest;
    // Name of the CG Group
    private String cgName;
    // CG URI
    private URI cgUri;
    // Project of the source volume
    private URI project;
    // Tenant making request
    private URI tenant;
    // Top-level policy for the CG
    public CGPolicyParams cgPolicy;
    // List of copies
    private List<CreateCopyParams> copies;
    // List of replication sets that make up this consistency group.
    private List<CreateRSetParams> rsets;

    public CGRequestParams() {
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

    public URI getCgUri() {
        return cgUri;
    }

    public void setCgUri(URI cgUri) {
        this.cgUri = cgUri;
    }

    public URI getProject() {
        return project;
    }

    public void setProject(URI project) {
        this.project = project;
    }

    public URI getTenant() {
        return tenant;
    }

    public void setTenant(URI tenant) {
        this.tenant = tenant;
    }

    public List<CreateCopyParams> getCopies() {
        return copies;
    }

    public void setCopies(List<CreateCopyParams> copies) {
        this.copies = copies;
    }

    public List<CreateRSetParams> getRsets() {
        return rsets;
    }

    public void setRsets(List<CreateRSetParams> rsets) {
        this.rsets = rsets;
    }

    // The top-level CG policy objects
    public static class CGPolicyParams implements Serializable {
        public String copyMode;
        public Long rpoValue;
        public String rpoType;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("\ncgName: " + cgName);
        sb.append("\nproject: " + project);
        sb.append("\ntenant: " + tenant);
        sb.append("\n---------------\n");
        if (copies != null) {
            for (CreateCopyParams copy : copies) {
                sb.append(copy);
                sb.append("\n");
            }
        }
        sb.append("\n---------------\n");
        if (rsets != null) {
            for (CreateRSetParams rset : rsets) {
                sb.append(rset);
                sb.append("\n");
            }
        }
        sb.append("\n---------------\n");
        return sb.toString();
    }
}
