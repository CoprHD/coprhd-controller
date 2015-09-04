/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.List;

public interface ServiceItemContainerRestRep {
    public List<? extends ServiceItemRestRep> getItems();
}
