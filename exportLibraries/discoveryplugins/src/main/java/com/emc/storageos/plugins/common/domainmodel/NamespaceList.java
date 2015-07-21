/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.plugins.common.domainmodel;


import java.util.Map;

public class NamespaceList {
    private Map<String,Object> _nsList;

    public void setNsList(Map<String,Object> nsList) {
        this._nsList = nsList;
    }

    public Map<String,Object> getNsList() {
        return _nsList;
    }
}
