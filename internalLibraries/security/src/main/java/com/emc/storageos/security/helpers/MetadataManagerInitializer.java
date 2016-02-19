/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.helpers;

import org.springframework.security.saml.metadata.CachingMetadataManager;

public class MetadataManagerInitializer {
    private CachingMetadataManager cachingMetadataManager;

    public CachingMetadataManager getCachingMetadataManager() {
        return cachingMetadataManager;
    }

    public void setCachingMetadataManager(CachingMetadataManager cachingMetadataManager) {
        this.cachingMetadataManager = cachingMetadataManager;
    }
}
