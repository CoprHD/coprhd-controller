/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.util;

import java.util.Comparator;

import org.apache.commons.lang.ObjectUtils;

import com.emc.storageos.db.client.model.ModelObject;

public class LastUpdatedComparator implements Comparator<ModelObject> {
    private boolean descending;

    public LastUpdatedComparator() {
        this(true);
    }

    public LastUpdatedComparator(boolean descending) {
        this.descending = descending;
    }

    @Override
    public int compare(ModelObject a, ModelObject b) {
        int result = ObjectUtils.compare(a.getLastUpdated(), b.getLastUpdated());
        return descending ? -result : result;
    }

    /** Comparator that returns newest updated objects first. */
    public static final LastUpdatedComparator NEWEST = new LastUpdatedComparator(true);
    /** Comparator that puts oldest updated objects first. */
    public static final LastUpdatedComparator OLDEST = new LastUpdatedComparator(false);
}
