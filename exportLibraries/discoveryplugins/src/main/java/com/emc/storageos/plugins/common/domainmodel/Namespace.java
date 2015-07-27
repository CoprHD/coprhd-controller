/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins.common.domainmodel;

import java.util.List;

public class Namespace {
    private List<Object> _operations;

    public void setOperations(List<Object> _operations) {
        this._operations = _operations;
    }

    public List<Object> getOperations() {
        return _operations;
    }

   
}
