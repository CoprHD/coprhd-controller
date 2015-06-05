/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

import com.emc.storageos.db.client.model.DataObjectWithACLs;
import com.emc.storageos.db.client.model.Name;

import java.util.Date;

public abstract class ModelObjectWithACLs extends DataObjectWithACLs {
    public static final String LAST_UPDATED = "lastUpdated";

    private Date lastUpdated;

    /**
     * Marks the object as updated.
     */
    public void markUpdated() {
        setLastUpdated(new Date());
    }

    @Name(LAST_UPDATED)
    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
        setChanged(LAST_UPDATED);
    }
    
    public abstract Object[] auditParameters();
}
