/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
