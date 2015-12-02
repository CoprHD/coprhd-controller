/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.object;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "bucket_acl_update")
public class ObjectBucketACLUpdateParams extends BucketACLUpdateParams {

    private static final long serialVersionUID = 3325512635881664535L;

    public ObjectBucketACLUpdateParams() {

    }

}
