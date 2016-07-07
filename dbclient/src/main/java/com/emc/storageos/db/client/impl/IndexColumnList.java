/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.impl;

import java.util.List;
import java.util.Map;

public interface IndexColumnList {

    void add(String key, CompositeColumnName column);

    Map<String, List<CompositeColumnName>> getColumnsToClean();

    Map<String, List<CompositeColumnName>> getAllColumns(String key);

    boolean isEmpty();

}
