/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.xtremio;

import java.io.Serializable;

import com.emc.storageos.volumecontroller.impl.utils.ExportOperationContext;

public class XtremIOExportOperationContext extends ExportOperationContext implements Serializable {

    private static final long serialVersionUID = -8126110014637225895L;

    public static final String OPERATION_ADD_INITIATORS_TO_INITIATOR_GROUP = "Add initiators to initiator group";
    public static final String OPERATION_CREATE_INITIATOR_GROUP = "Create initiator group";
    public static final String OPERATION_ADD_VOLUMES_TO_INITIATOR_GROUP = "Add volumes to initiator group";

}
