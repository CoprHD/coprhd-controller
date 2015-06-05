/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.util.Date;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;

public abstract class ModelObject extends DataObject {
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
    
    public Object[] auditParameters() {
        return new Object[] {getLabel(), getId()};
    }
}
