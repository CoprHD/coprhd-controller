/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.common.diff;

import javax.xml.bind.annotation.XmlAttribute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.common.schema.SchemaObject;

public class PrimitiveChangeTracker<T> extends Diff {
    private static final Logger log = LoggerFactory.getLogger(PrimitiveChangeTracker.class);

    private T oldValue;
    private T newValue;
    private SchemaObject changedObj;

    public static <T> PrimitiveChangeTracker<T> newInstance(T oldValue, T newValue, SchemaObject changedObj) {
        if (oldValue.equals(newValue))
            return null;
        
        return new PrimitiveChangeTracker<T>(oldValue, newValue, changedObj);
    }

    private PrimitiveChangeTracker() {
    }

    private PrimitiveChangeTracker(T oldValue, T newValue, SchemaObject changedObj) {
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changedObj = changedObj;
    }

    @XmlAttribute(name = "old_value")
    public String getOldValue() {
        return oldValue.toString();
    }

    @XmlAttribute(name = "new_value")
    public String getNewValue() {
        return newValue.toString();
    }

    public boolean isUpgradable() {
        if (!oldValue.equals(newValue)) {
            log.info("An unsupported schema change has been made. The value or type of " + changedObj.describe()
                    + " has been changed from " + oldValue.toString() + " to " + newValue.toString());
            return false;
        } else {
            return true;
        }
    }

    public boolean isChanged() {
        return !oldValue.equals(newValue);
    }
}
