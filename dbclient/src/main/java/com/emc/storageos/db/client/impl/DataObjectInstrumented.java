/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import com.emc.storageos.db.client.model.DataObject;

/**
 * @author cgarber
 * 
 */
public interface DataObjectInstrumented<E extends DataObject> {

    void initLazyLoading(LazyLoader loader);

    void enableLazyLoading();

    void load(String lazyLoadedFieldName, DataObject obj);

    void refreshMappedByField(String lazyLoadedFieldName, DataObject obj);

    void invalidate(String lazyLoadedFieldName);
}
