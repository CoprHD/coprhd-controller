/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class CifsShareModifyParam {
    private VNXeBase cifsShare;
    private CifsShareParam cifsShareParameters;

    public VNXeBase getCifsShare() {
        return cifsShare;
    }

    public void setCifsShare(VNXeBase cifsShare) {
        this.cifsShare = cifsShare;
    }

    public CifsShareParam getCifsShareParameters() {
        return cifsShareParameters;
    }

    public void setCifsShareParameters(CifsShareParam cifsShareParameters) {
        this.cifsShareParameters = cifsShareParameters;
    }

}
