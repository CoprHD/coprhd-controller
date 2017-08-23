/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.common;

public class RdfGroupIdType extends ParamType {

    // min/max occurs: 1/1
    private Long rdf_group_number;
    // min/max occurs: 1/1
    private String label;

    public Long getRdf_group_number() {
        return rdf_group_number;
    }

    public String getLabel() {
        return label;
    }
}
