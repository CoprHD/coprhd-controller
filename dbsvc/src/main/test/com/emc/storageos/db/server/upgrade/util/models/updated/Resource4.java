/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.util.models.updated;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;

@Cf("Resource4")
public class Resource4 extends DataObject {
    private String key;

    @Name("key")
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
        setChanged("key");
    }
}
