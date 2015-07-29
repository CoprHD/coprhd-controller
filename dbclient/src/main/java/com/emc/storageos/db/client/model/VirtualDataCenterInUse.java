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

package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.model.DbKeyspace.Keyspaces;

@Cf("VirtualDataCenterInUse")
@DbKeyspace(Keyspaces.GLOBAL)
public class VirtualDataCenterInUse extends DataObject {
    private Boolean inUse;

    @Name("inUse")
    public Boolean getInUse() {
        return inUse;
    }

    public void setInUse(Boolean inUse) {
        this.inUse = inUse;
        setChanged("inUse");
    }
}
