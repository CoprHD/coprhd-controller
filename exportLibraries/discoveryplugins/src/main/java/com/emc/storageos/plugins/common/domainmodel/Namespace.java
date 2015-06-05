/**
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

import java.util.List;

public class Namespace {
    private List<Object> _operations;

    public void set_operations(List<Object> _operations) {
        this._operations = _operations;
    }

    public List<Object> get_operations() {
        return _operations;
    }

   
}
