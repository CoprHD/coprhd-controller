/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.restvmax.vmax;

import com.emc.storageos.driver.restvmax.rest.RestEndPoint;
import com.emc.storageos.driver.restvmax.rest.RestMethod;
import com.emc.storageos.driver.restvmax.vmax.type.CreateStorageGroupParamType;
import com.emc.storageos.driver.restvmax.vmax.type.GenericResultType;

import java.util.Arrays;

public class RestVmaxEndpoint {
    public static final RestEndPoint SLOPROVISIONING_SYMMETRIX__STORAGEGROUP;
    static {
        SLOPROVISIONING_SYMMETRIX__STORAGEGROUP = new RestEndPoint(
                "/sloprovisioning/symmetrix/{symmetrixId}/storagegroup");
        /* TBD
        SLOPROVISIONING_SYMMETRIX__STORAGEGROUP.getActions().put(RestMethod.GET, null);
        */
        SLOPROVISIONING_SYMMETRIX__STORAGEGROUP.getActions().put(RestMethod.POST,
                Arrays.asList(CreateStorageGroupParamType.class, GenericResultType.class));
    }

    /*
    public static final RestEndPoint SLOPROVISIONING_SYMMETRIX__STORAGEGROUP__;
    static {
        SLOPROVISIONING_SYMMETRIX__STORAGEGROUP__ = new RestEndPoint(
                "/sloprovisioning/symmetrix/{symmetrixId}/storagegroup/{storageGroupId}");
        SLOPROVISIONING_SYMMETRIX__STORAGEGROUP__.getActions().put(RestMethod.GET, null);
        SLOPROVISIONING_SYMMETRIX__STORAGEGROUP__.getActions().put(RestMethod.PUT, null);
        SLOPROVISIONING_SYMMETRIX__STORAGEGROUP__.getActions().put(RestMethod.DELETE, null);
    }
    */
}
