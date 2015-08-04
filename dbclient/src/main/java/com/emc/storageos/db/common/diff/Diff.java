/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.common.diff;

public abstract class Diff {
    public abstract boolean isUpgradable();

    public abstract boolean isChanged();
}
