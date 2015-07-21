/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.util.models.updated2;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.RelationIndex;
import com.emc.storageos.db.client.upgrade.CustomMigrationCallback;
import com.emc.storageos.db.server.upgrade.util.callbacks2.Resource3NewFlagsInitializer;
import java.net.URI;

@Cf("Resource3")
public class Resource3 extends DataObject {
    private URI res4; // Test custom callback execution order
    private Long extraFlags; // Test custom callback execution order
    private Long newFlags; // Test custom callback execution order
    
    // Deliberately put this up front to make sure that the field order doesn't matter
    @Name("newFlags")
    public Long getNewFlags() {
        return newFlags;
    }

    public void setNewFlags(Long newFlags) {
        this.newFlags = newFlags;
        setChanged("newFlags");
    }

    @Name("extraFlags")
    public Long getExtraFlags() {
        return extraFlags;
    }

    public void setExtraFlags(Long extraFlags) {
        this.extraFlags = extraFlags;
        setChanged("extraFlags");
    }

    @Name("res4")
    @RelationIndex(cf = "TestRelationIndex", type = Resource4.class)
    public URI getRes4() {
        return res4;
    }

    public void setRes4(URI res4) {
        this.res4 = res4;
        setChanged("res4");
    }

}
