/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.server.upgrade.util.models.updated2;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.upgrade.CustomMigrationCallback;

@Cf("Resource4")
public class Resource4 extends DataObject {
    private String key;

    @AlternateId("TestAltIdIndex2")
    @Name("key")
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
        setChanged("key");
    }
}
