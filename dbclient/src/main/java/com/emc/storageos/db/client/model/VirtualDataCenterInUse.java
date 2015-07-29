/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
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
