/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.system;

import com.emc.storageos.driver.univmax.rest.type.common.GenericResultImplType;

public class ListSymmetrixResultType extends GenericResultImplType {

    // min/max occurs: 0/unbounded
    private String[] symmetrixId;
    // min/max occurs: 0/1
    private Integer num_of_symmetrix_arrays;

    public String[] getSymmetrixId() {
        return symmetrixId;
    }

    public Integer getNum_of_symmetrix_arrays() {
        return num_of_symmetrix_arrays;
    }
}
