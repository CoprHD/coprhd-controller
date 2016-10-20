/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.vnx;

import com.emc.storageos.volumecontroller.impl.utils.ExportOperationContext;

public class VnxExportOperationContext extends ExportOperationContext {

    private static final long serialVersionUID = 3389983301526698992L;

    public static final String OPERATION_ADD_INITIATORS_TO_STORAGE_GROUP = "Add initiators to storage group";
    public static final String OPERATION_CREATE_STORAGE_GROUP = "Create storage group";
    public static final String OPERATION_ADD_VOLUMES_TO_STORAGE_GROUP = "Add volumes to storage group";

}
