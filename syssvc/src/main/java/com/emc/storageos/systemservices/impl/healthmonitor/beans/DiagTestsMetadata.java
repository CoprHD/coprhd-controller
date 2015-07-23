/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

    public void setMetadata(LinkedHashMap<String, DiagTestMetadata> metadata){
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
