/*
 * Copyright (c) 2016 Intel Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Interface for migration service calls.
 */
public interface MigrationServiceApi {
    /**
     * Define the default MigrationServiceApi implementation.
     */
    public static final String DEFAULT = "default";

    /**
     * Determines whether or not the virtual array for the passed volume can be
     * changed to the passed virtual array. Throws a APIException when the
     * varray change is not supported.
     *
     * @param volume A reference to the volume.
     * @param newVarray A reference to the new varray.
     *
     * @throws APIException
     */
    public void verifyVarrayChangeSupportedForVolumeAndVarray(Volume volume,
            VirtualArray newVarray) throws APIException;

}
