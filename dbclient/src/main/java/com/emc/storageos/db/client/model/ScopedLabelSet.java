/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
