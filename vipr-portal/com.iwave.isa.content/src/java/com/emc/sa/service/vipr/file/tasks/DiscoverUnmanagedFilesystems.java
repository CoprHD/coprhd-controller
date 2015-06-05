/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import com.emc.sa.service.vipr.tasks.DiscoverUnmanaged;

public class DiscoverUnmanagedFilesystems extends DiscoverUnmanaged {
    public DiscoverUnmanagedFilesystems(String storageSystemId) {
        super(storageSystemId, UnmanagedNamespace.UNMANAGED_FILESYSTEMS);
    }
}
