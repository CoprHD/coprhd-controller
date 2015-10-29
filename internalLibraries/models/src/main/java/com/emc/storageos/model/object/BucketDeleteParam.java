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
 * @valid true
 * @valid false
 */
@XmlRootElement(name = "bucket_deactivate")
public class BucketDeleteParam {

    private boolean forceDelete;

    public BucketDeleteParam() {
    }

    public BucketDeleteParam(boolean forceDelete) {
        this.forceDelete = forceDelete;
    }

    @XmlElement(name = "forceDelete")
    public boolean getForceDelete() {
        return forceDelete;
    }

    public void setForceDelete(boolean forceDelete) {
        this.forceDelete = forceDelete;
    }

}
