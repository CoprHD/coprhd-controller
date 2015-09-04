/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.util.models.updated2;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;

@Cf("Resource5")
public class Resource5 extends DataObject {
    private String test;

    @Name("test")
    public String getTest() {
        return test;
    }

    public void setTest(String test) {
        this.test = test;
        setChanged("test");
    }
}
