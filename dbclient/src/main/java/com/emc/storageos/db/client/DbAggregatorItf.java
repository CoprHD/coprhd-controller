/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client;

import java.util.List;

import com.emc.storageos.db.client.impl.CompositeColumnName;

/**
 */
public interface DbAggregatorItf {

    void aggregate(List<CompositeColumnName> columns);

    String[] getAggregatedFields();
}
