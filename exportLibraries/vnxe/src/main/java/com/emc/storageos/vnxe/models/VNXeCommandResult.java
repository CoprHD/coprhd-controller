/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

/*
 * This class is for sync provision calls result
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXeCommandResult {
    private VNXeBase storageResource;
    private String id;
    private boolean success;

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

}
