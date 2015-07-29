/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class NfsShareDeleteParam {
    private VNXeBase nfsShare;

    public VNXeBase getNfsShare() {
        return nfsShare;
    }

    public void setNfsShare(VNXeBase nfsShare) {
        this.nfsShare = nfsShare;
    }

}
