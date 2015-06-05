/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.providerfinders;

import com.emc.storageos.db.client.model.StorageSystem;

/**
 * Implementations of this interface should determine the best available SMI-S provider to use given
 * an SRDF-configured ViPR Volume.
 *
 * Created by bibbyi1 on 3/24/2015.
 */
public interface FindProviderStrategy {
    StorageSystem find();
}
