/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vnxe;

import java.io.Serializable;

import com.emc.storageos.volumecontroller.impl.utils.ExportOperationContext;

public class VNXeExportOperationContext extends ExportOperationContext implements Serializable {

    private static final long serialVersionUID = -7334547191614471700L;
    public static final String OPERATION_ADD_INITIATORS_TO_HOST = "Add initiators to host";
    public static final String OPERATION_ADD_VOLUMES_TO_HOST_EXPORT = "Add volumes to host export";

}
