/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.util;

import java.util.Comparator;

import com.emc.storageos.db.client.model.uimodels.SortedIndexDataObject;
import org.apache.commons.beanutils.BeanComparator;
import org.apache.commons.collections.comparators.NullComparator;

public class SortedIndexComparator implements Comparator<SortedIndexDataObject> {

    private static final BeanComparator COMPARATOR = new BeanComparator(SortedIndexDataObject.SORTED_INDEX_PROPERTY_NAME,
            new NullComparator());

    @Override
    public int compare(SortedIndexDataObject o1, SortedIndexDataObject o2) {
        return COMPARATOR.compare(o1, o2);
    }

}
