/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

/**
 * Set of scoped labes
 */
public class ScopedLabelSet extends AbstractChangeTrackingSet<ScopedLabel> {

    public ScopedLabelSet() {
        super();
    }

    public ScopedLabelSet(ScopedLabelSet other) {
        super(other);
    }

    @Override
    public ScopedLabel valFromString(String value) {
        return ScopedLabel.fromString(value);
    }

    @Override
    public String valToString(ScopedLabel value) {
        return value.toString();
    }
}
