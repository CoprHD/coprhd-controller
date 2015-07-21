/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 * Parameters for creating lun
 *
 */
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
public class LunCreateParam extends ParamBase{
    private String description;
    private LunParam lunParameters;
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public LunParam getLunParameters() {
        return lunParameters;
    }
    public void setLunParameters(LunParam lunParameters) {
        this.lunParameters = lunParameters;
    }
    
    
    
}
