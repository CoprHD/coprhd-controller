/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.object;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Whether or not the Bucket should be forcibly deleted. If set to yes, Bucket will be deleted
 * 
 */
@XmlRootElement(name = "bucket_deactivate")
public class BucketDeleteParam {

    private static final String DELETE_TYPE = "FULL";
    
    private boolean forceDelete;
    private String deleteType = DELETE_TYPE;

    public BucketDeleteParam() {
    }
    
    public BucketDeleteParam(boolean forceDelete) {
        this.forceDelete = forceDelete;
    }

    public BucketDeleteParam(boolean forceDelete, String deleteType) {
        this.forceDelete = forceDelete;
        this.deleteType = deleteType;
    }


    @XmlElement(required = false, name = "forceDelete")
    public boolean getForceDelete() {
        return forceDelete;
    }

    public void setForceDelete(boolean forceDelete) {
        this.forceDelete = forceDelete;
    }
    
    @XmlElement(required = false, name = "delete_type")
    public String getDeleteType() {
        return deleteType;
    }

    public void setDeleteType(String deleteType) {
        this.deleteType = deleteType;
    }
}
