/**
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

import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
public class LunSnapCreateParam extends ParamBase {
	
	private VNXeBase storageResource;
    private String name;
    private String description;
    private Boolean autoDelete;
    private Long  retentionDuration;
    
    public VNXeBase getStorageResource() {
        return storageResource;
    }
    public void setStorageResource(VNXeBase storageResource) {
        this.storageResource = storageResource;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public Boolean getAutoDelete() {
        return autoDelete;
    }
    public void setAutoDelete(Boolean autoDelete) {
        this.autoDelete = autoDelete;
    }
    public Long getRetentionDuration() {
        return retentionDuration;
    }
    public void setRetentionDuration(Long retentionDuration) {
        this.retentionDuration = retentionDuration;
    }
    
}
