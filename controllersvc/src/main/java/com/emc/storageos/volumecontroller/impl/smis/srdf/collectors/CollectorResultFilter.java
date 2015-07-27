/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf.collectors;

import com.emc.storageos.db.client.model.StorageSystem;

import java.util.Collection;

/**
 * Created by bibbyi1 on 4/23/2015.
 */
public interface CollectorResultFilter<T> {
    Collection<T> filter(Collection<T> results, StorageSystem provider);
}
