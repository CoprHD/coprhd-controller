/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

/*
 * This class is for sync provision calls result
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXeCommandResult {
    private VNXeBase storageResource;
    private String id;
    private boolean success;
    private String sid;
    private List<CifsShareACE> cifsShareACEs;

    public VNXeBase getStorageResource() {
        return storageResource;
    }

    public void setStorageResource(VNXeBase storageResource) {
        this.storageResource = storageResource;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean getSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public List<CifsShareACE> getCifsShareACEs() {
        return cifsShareACEs;
    }

    public void setCifsShareACEs(List<CifsShareACE> cifsShareACEs) {
        this.cifsShareACEs = cifsShareACEs;
    }

}
