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

package com.emc.storageos.db.client.model;

import java.util.Set;

/**
 */
@Cf("NodeObj")
public class NodeObj extends DataObject {
    private String _description;

    @Name("description")
    public String getDescription() {
        return _description;
    }

    public void setDescription(String description) {
        _description = description;
        setChanged("description");
    }

}
