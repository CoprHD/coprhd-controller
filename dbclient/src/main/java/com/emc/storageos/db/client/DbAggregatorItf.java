/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client;

import com.emc.storageos.db.client.javadriver.CassandraRows;

/**
 */
public interface DbAggregatorItf {

    void aggregate(CassandraRows rows);

    String[] getAggregatedFields();
}
