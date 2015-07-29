/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@XmlRootElement
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class CifsShareCreateParam extends ParamBase {
    private String path;
    private VNXeBase cifsServer;
    private CifsShareParam cifsShareParameters;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public VNXeBase getCifsServer() {
        return cifsServer;
    }

    public void setCifsServer(VNXeBase cifsServer) {
        this.cifsServer = cifsServer;
    }

    public CifsShareParam getCifsShareParameters() {
        return cifsShareParameters;
    }

    public void setCifsShareParameters(CifsShareParam cifsShareParameters) {
        this.cifsShareParameters = cifsShareParameters;
    }

}
