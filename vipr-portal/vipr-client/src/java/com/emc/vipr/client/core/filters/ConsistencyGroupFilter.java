/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core.filters;

import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;

/**
 * Filters consistency groups to return all that match the specific type and option
 * to also match on consistency group without any types.
 */
public class ConsistencyGroupFilter extends DefaultResourceFilter<BlockConsistencyGroupRestRep> {
    private final String type;
    private final boolean allowEmptyType;

    /**
     * Creates a filter with specific type and option to accept consistency groups with no types
     * 
     * @param type consistency group type to filter by
     * @param allowEmptyType if true, accept consistency groups with no types
     */
    public ConsistencyGroupFilter(String type, boolean allowEmptyType) {
        this.type = type;
        this.allowEmptyType = allowEmptyType;
    }

    @Override
    public boolean accept(BlockConsistencyGroupRestRep item) {
        if ((allowEmptyType && item.getTypes().isEmpty())
                || (type != null && item.getTypes().contains(type))) {
            return true;
        } else {
            return false;
        }
    }
}
