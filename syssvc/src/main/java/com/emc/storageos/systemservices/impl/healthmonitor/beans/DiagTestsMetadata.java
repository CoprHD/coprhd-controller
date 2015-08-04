/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.healthmonitor.beans;

import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.collect.ImmutableMap;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Class that holds metadata for all diagnostic tests.
 */
public class DiagTestsMetadata {

    private static volatile Map<String, DiagTestMetadata> _metadata = null;

    public void setMetadata(LinkedHashMap<String, DiagTestMetadata> metadata) {
        if (_metadata == null) {
            _metadata = ImmutableMap.copyOf(metadata);
        }
    }

    public static Map<String, DiagTestMetadata> getMetadata() {
        if (_metadata == null) {
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Diagnostic tests metadata");
        }
        return _metadata;
    }
}
