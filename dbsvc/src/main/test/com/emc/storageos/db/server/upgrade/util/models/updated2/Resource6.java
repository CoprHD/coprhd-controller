/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.util.models.updated2;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.upgrade.CustomMigrationCallback;
import com.emc.storageos.db.server.upgrade.util.callbacks2.Resource3NewFlagsInitializer;

@Cf("Resource6")
public class Resource6 extends Resource3{
    private Long dupTestFlags; // Test duplicated custom callback

    @Name("dupTestFlags")
    public Long getDupTestFlags() {
        return dupTestFlags;
    }

    public void setDupTestFlags(Long dupFlags) {
        this.dupTestFlags = dupFlags;
        setChanged("dupTestFlags");
    }
}
