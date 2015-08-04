/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import com.netflix.astyanax.model.Column;

import java.util.List;
import java.util.Map;

/**
 * Created by zeldib on 8/20/2014.
 */
public interface IndexColumnList {

    void add(String key, Column<CompositeColumnName> column);

    Map<String, List<Column<CompositeColumnName>>> getColumnsToClean();

    Map<String, List<Column<CompositeColumnName>>> getAllColumns(String key);

    boolean isEmpty();

}
