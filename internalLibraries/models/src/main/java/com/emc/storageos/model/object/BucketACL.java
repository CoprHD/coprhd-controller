/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.object;

import java.io.Serializable;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "bucket_acl")
public class BucketACL implements Serializable {

    private static final long serialVersionUID = -7903490453952137563L;
    private List<BucketACE> bucketACL;

    @XmlElementWrapper(name = "acl")
    @XmlElement(name = "ace")
    public List<BucketACE> getBucketACL() {
        return bucketACL;
    }

    public void setBucketACL(List<BucketACE> bucketACL) {
        this.bucketACL = bucketACL;
    }

}
