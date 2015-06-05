/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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