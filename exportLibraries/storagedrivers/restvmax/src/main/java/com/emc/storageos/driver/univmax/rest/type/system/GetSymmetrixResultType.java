/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.system;

import com.emc.storageos.driver.univmax.rest.type.common.GenericResultImplType;

public class GetSymmetrixResultType extends GenericResultImplType {

    // min/max occurs: 0/unbounded
    private SymmetrixType[] symmetrix;

    public SymmetrixType[] getSymmetrix() {
        return symmetrix;
    }
}
