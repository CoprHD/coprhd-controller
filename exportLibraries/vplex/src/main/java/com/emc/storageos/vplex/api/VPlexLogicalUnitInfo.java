/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

/**
 * Info for a VPlex Logical Unit
 */
public class VPlexLogicalUnitInfo extends VPlexResourceInfo {

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("LogicalUnitInfo ( ");
        str.append(super.toString());
        str.append(" )");
        return str.toString();
    }
}
