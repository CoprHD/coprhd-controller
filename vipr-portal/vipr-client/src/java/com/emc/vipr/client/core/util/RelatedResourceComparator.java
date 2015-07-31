/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.util;

import java.util.Comparator;

import com.emc.storageos.model.RelatedResourceRep;

public class RelatedResourceComparator implements Comparator<RelatedResourceRep> {
    @Override
    public int compare(RelatedResourceRep first, RelatedResourceRep second) {
        return first.getId().compareTo(second.getId());
    }
}
