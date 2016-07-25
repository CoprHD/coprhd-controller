/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.util;

import java.util.Comparator;

import com.emc.storageos.db.client.model.uimodels.Order;
import org.apache.commons.lang.ObjectUtils;

import com.emc.storageos.db.client.model.ModelObject;

public class ScheduledTimeComparator implements Comparator<Order> {
    public ScheduledTimeComparator() {
    }

    @Override
    public int compare(Order a, Order b) {
        int result = ObjectUtils.compare(a.getScheduledTime(), b.getScheduledTime());
        return result;
    }

    /** Comparator that puts oldest scheduled orders first. */
    public static final ScheduledTimeComparator OLDEST = new ScheduledTimeComparator();
}
