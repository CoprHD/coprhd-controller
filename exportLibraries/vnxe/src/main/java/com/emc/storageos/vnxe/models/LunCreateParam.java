/*
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

/**
 * Parameters for creating lun
 * 
 */
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class LunCreateParam extends ParamBase {
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
