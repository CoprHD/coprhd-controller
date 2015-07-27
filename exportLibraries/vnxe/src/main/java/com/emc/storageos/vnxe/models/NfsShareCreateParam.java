/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@XmlRootElement
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
public class NfsShareCreateParam extends ParamBase{
    private String path;
    private NfsShareParam nfsShareParameters;
    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }
    public NfsShareParam getNfsShareParameters() {
        return nfsShareParameters;
    }
    public void setNfsShareParameters(NfsShareParam nfsShareParameters) {
        this.nfsShareParameters = nfsShareParameters;
    }
    
    
}
