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
    public static final String SLOPROVISIONING_SYMMETRIX__STORAGEGROUP = "/sloprovisioning/symmetrix/%s/storagegroup";
    public static final String SLOPROVISIONING_SYMMETRIX__VOLUME_QUERY = "/sloprovisioning/symmetrix/%s/volume?storageGroupId=%s";
    public static final String SLOPROVISIONING_SYMMETRIX__VOLUME_ID = "/sloprovisioning/symmetrix/%s/volume/%s";
    public static final String SYSTEM__VERSION = "/system/version";

}
