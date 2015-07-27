/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
