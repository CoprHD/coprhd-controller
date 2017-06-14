/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller;

import com.emc.storageos.volumecontroller.impl.utils.ExportOperationContext;

public class VplexExportOperationContext extends ExportOperationContext {

    private static final long serialVersionUID = 3389983301526698992L;

    public static final String OPERATION_ADD_INITIATORS_TO_STORAGE_VIEW = "Add initiators to storage view";
    public static final String OPERATION_CREATE_STORAGE_VIEW = "Create storage view";
    public static final String OPERATION_ADD_VOLUMES_TO_STORAGE_VIEW = "Add volumes to storage view";
    public static final String OPERATION_ADD_TARGETS_TO_STORAGE_VIEW = "Add targets to storage view";

}