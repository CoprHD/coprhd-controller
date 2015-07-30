/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

/**
 * Interface for block service calls that service larger requests, such as
 * creating a volume.
 * 
 */
public interface ExportGroupServiceApi {
    /**
     * Define the default ExportGroupServiceApi implementation.
     */
    public static final String DEFAULT = "default";

    /**
     * Validate varray ports during Export Group Create and Update
     * 
     * @param storageSystemURIs storageSystem URIs
     * @param varray source VirtualArray
     * @param varrayStoragePorts tagged storage ports in varray
     * @throws InternalException
     */
    public void validateVarrayStoragePorts(Set<URI> storageSystemURIs,
            VirtualArray varray, List<URI> allHosts)
            throws InternalException;
}