/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client;

import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.netflix.astyanax.model.Row;

/**
 */
public interface DbAggregatorItf {

    void aggregate(Row<String, CompositeColumnName> row);

    String[] getAggregatedFields();
}
