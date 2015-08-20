/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

import java.net.URI;

public interface SortedIndexDataObject {

    public static final String SORTED_INDEX_PROPERTY_NAME = "sortedIndex";

    public URI getId();

    public Integer getSortedIndex();

    public void setSortedIndex(Integer sortedIndex);
}
