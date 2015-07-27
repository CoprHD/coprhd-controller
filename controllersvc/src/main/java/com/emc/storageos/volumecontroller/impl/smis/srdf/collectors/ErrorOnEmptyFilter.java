/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf.collectors;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.smis.srdf.exceptions.NoSynchronizationsFoundException;

import java.util.Collection;

/**
 * Created by bibbyi1 on 4/23/2015.
 */
public class ErrorOnEmptyFilter implements CollectorResultFilter {

    @Override
    public Collection filter(Collection results, StorageSystem provider) {
        if (results.isEmpty()) {
            throw new NoSynchronizationsFoundException("Expected collection results to not be empty");
        }
        return results;
    }
}
